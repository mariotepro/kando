/* ── State ────────────────────────────────────────────────────────────────── */
let editMode = false;
let currentTaskId = null;
let currentTaskColumnId = null;
let modalOriginColumnId = null;
let currentParentTaskId = null;
let currentLabelId = null;
let columnSortable = null;
let dragState = null;
let suppressTaskClickUntil = 0;
const taskSortables = [];
const QUICK_LABEL_PATTERN = /#([\w\-áéíóúüñÁÉÍÓÚÜÑ]+)/u;
const QUICK_LABEL_PATTERN_GLOBAL = /#([\w\-áéíóúüñÁÉÍÓÚÜÑ]+)/gu;
const DEFAULT_LABEL_COLOR = '#cba6f7';
const RECENT_LABEL_LIMIT = 4;
const LABEL_RESULTS_LIMIT = 12;
const PARENT_RESULTS_LIMIT = 10;
const PICKER_VIEWPORT_GAP = 12;
const PICKER_PANEL_OFFSET = 8;
const PICKER_MIN_WIDTH = 320;
const PICKER_MAX_WIDTH = 520;
const SUBTASK_DROP_BAND_RATIO = 0.18;
const SUBTASK_DROP_Y_MARGIN = 14;
const SORT_DIRECTION_NONE = 'none';
const SORT_DIRECTION_ASC = 'asc';
const SORT_DIRECTION_DESC = 'desc';
const COLUMN_SORT_STORAGE_KEY = 'kando_column_sort_directions';
const HISTORY_EVENT_CREATED = 'CREATED';
let boardSearchTerm = '';
let boardFilterLabelId = null;
let activeCpickerId = null;

/* ── Init ─────────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  initTaskSortables();
  bindTaskCardInteractions();
  bindModalPickers();
  syncBoardCentering();
  syncSortButtons();
  bindBoardFilters();
  bindProfileDropdown();

  document.getElementById('btnCreateTask').addEventListener('click', openCreateModal);
  window.addEventListener('resize', syncBoardCentering);
  window.addEventListener('resize', syncOpenCpickerPosition);
  document.addEventListener('scroll', syncOpenCpickerPosition, true);

  document.querySelectorAll('.quick-add-input').forEach(bindQuickAddInput);
  document.querySelectorAll('.quick-add-card').forEach(bindQuickAddCard);
});

function syncBoardCentering() {
  const wrapper = document.querySelector('.board-wrapper');
  const board = document.getElementById('board');
  if (!wrapper || !board) {
    return;
  }

  board.classList.toggle('board-centered', board.scrollWidth <= wrapper.clientWidth + 8);
}

/* ── Drag-and-drop tasks ──────────────────────────────────────────────────── */
function initTaskSortables() {
  document.querySelectorAll('.task-list').forEach(list => {
    const sortable = Sortable.create(list, {
      group: 'tasks',
      draggable: '.task-card[data-task-id]',
      animation: 160,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      handle: '.task-card[data-task-id]',
      filter: '.task-add-subtask, .task-delete-btn, .subtask-complete-btn',
      preventOnFilter: false,
      onStart(evt) {
        dragState = buildDragState(evt.item);
      },
      onMove(evt) {
        updateSubtaskDropTarget(evt);
        return true;
      },
      onEnd(evt) {
        const taskId = parseInt(evt.item.dataset.taskId, 10);
        const targetColId = parseInt(evt.to.dataset.colId, 10);
        const candidate = resolveSubtaskCandidate(evt, evt.item);
        const parentTaskId = candidate
          ? parseInt(candidate.dataset.taskId, 10)
          : (dragState?.pendingParentTaskId || null);

        applyDragDomState(evt, parentTaskId);

        const newPosition = getTaskIndex(evt.item);
        suppressTaskClickUntil = Date.now() + 220;

        api('POST', `/api/tasks/${taskId}/move`, {
          targetColumnId: targetColId,
          newPosition,
          parentTaskId
        }).catch(() => location.reload())
          .finally(() => {
            clearTaskDropTargets();
            dragState = null;
          });
      }
    });
    taskSortables.push(sortable);
  });
}

function buildDragState(item) {
  return {
    childElements: getDirectSubtaskElements(item),
    pendingParentTaskId: null,
    sourceColId: parseInt(item.closest('.task-list').dataset.colId, 10),
    sourceParentTaskId: item.dataset.parentTaskId ? parseInt(item.dataset.parentTaskId, 10) : null,
    wasSubtask: Boolean(item.dataset.parentTaskId)
  };
}

function updateSubtaskDropTarget(evt) {
  if (!dragState) {
    return true;
  }

  const candidate = resolveSubtaskCandidate(evt, evt.dragged);
  clearTaskDropTargets();

  if (!candidate) {
    dragState.pendingParentTaskId = null;
    return true;
  }

  dragState.pendingParentTaskId = parseInt(candidate.dataset.taskId, 10);
  candidate.classList.add('task-card-drop-target');
  return true;
}

function resolveSubtaskCandidate(evt, draggedItem) {
  const point = resolveInteractionPoint(evt.originalEvent, draggedItem);
  const pointerCandidate = getTaskCardFromPoint(point, draggedItem);
  if (isValidSubtaskCandidate(pointerCandidate, draggedItem, point)) {
    return pointerCandidate;
  }

  const listCandidate = getTaskCardFromListPoint(evt.to, point, draggedItem);
  if (isValidSubtaskCandidate(listCandidate, draggedItem, point)) {
    return listCandidate;
  }

  const relatedCandidate = evt.related;
  if (isValidSubtaskCandidate(relatedCandidate, draggedItem, point)) {
    return relatedCandidate;
  }

  return null;
}

function getTaskCardFromPoint(point, draggedItem) {
  if (!point) {
    return null;
  }

  const target = document.elementFromPoint(point.x, point.y);
  if (!target) {
    return null;
  }

  const card = target.closest('.task-card');
  return card && card !== draggedItem && card.dataset.taskId ? card : null;
}

function getTaskCardFromListPoint(list, point, draggedItem) {
  if (!list || !point) {
    return null;
  }

  return [...list.querySelectorAll('.task-card[data-task-id]')].find(card => {
    if (card === draggedItem) {
      return false;
    }

    const rect = card.getBoundingClientRect();
    return point.y >= rect.top - SUBTASK_DROP_Y_MARGIN
      && point.y <= rect.bottom + SUBTASK_DROP_Y_MARGIN;
  }) || null;
}

function getPointerCoordinates(pointerEvent) {
  if (!pointerEvent) {
    return null;
  }

  const source = pointerEvent.changedTouches?.[0]
    || pointerEvent.touches?.[0]
    || pointerEvent;

  if (typeof source.clientX !== 'number' || typeof source.clientY !== 'number') {
    return null;
  }

  return {
    x: source.clientX,
    y: source.clientY
  };
}

function resolveInteractionPoint(pointerEvent, fallbackElement) {
  const point = getPointerCoordinates(pointerEvent);
  if (point) {
    return point;
  }

  const rect = fallbackElement?.getBoundingClientRect();
  if (!rect) {
    return null;
  }

  return {
    x: rect.left + rect.width * 0.56,
    y: rect.top + rect.height / 2
  };
}

function isValidSubtaskCandidate(candidate, draggedItem, point) {
  if (!candidate || !candidate.classList || !candidate.classList.contains('task-card') || !candidate.dataset.taskId) {
    return false;
  }

  if (candidate === draggedItem || candidate.dataset.parentTaskId) {
    return false;
  }

  if (dragState?.childElements.some(child => child.dataset.taskId === candidate.dataset.taskId)) {
    return false;
  }

  const draggedLabel = draggedItem.dataset.labelId;
  const candidateLabel = candidate.dataset.labelId;
  if (draggedLabel && candidateLabel && draggedLabel !== candidateLabel) {
    return false;
  }

  return isPointInSubtaskBand(candidate, point);
}

function isPointInSubtaskBand(card, point) {
  if (!point) {
    return false;
  }

  const rect = card.getBoundingClientRect();
  const bandTop = rect.top + rect.height * SUBTASK_DROP_BAND_RATIO;
  const bandBottom = rect.bottom - rect.height * SUBTASK_DROP_BAND_RATIO;
  return point.y >= bandTop - SUBTASK_DROP_Y_MARGIN
    && point.y <= bandBottom + SUBTASK_DROP_Y_MARGIN
    && point.x >= rect.left - 10
    && point.x <= rect.right + 10;
}

function applyDragDomState(evt, parentTaskId) {
  const card = evt.item;
  const targetList = evt.to;
  const targetColId = parseInt(targetList.dataset.colId, 10);

  if (parentTaskId) {
    const parentCard = targetList.querySelector(`.task-card[data-task-id="${parentTaskId}"]`);
    moveCardAfterSubtree(card, parentCard, targetList);
    setTaskAsSubtask(card, parentTaskId, targetColId);
    promoteDraggedChildrenToRoot();
    return;
  }

  if (shouldKeepExistingParent(card, targetList, targetColId)) {
    setTaskAsSubtask(card, dragState.sourceParentTaskId, targetColId);
    return;
  }

  setTaskAsRoot(card, targetColId);

  if (!dragState?.childElements.length) {
    return;
  }

  let anchor = card;
  dragState.childElements.forEach(child => {
    setTaskAsSubtask(child, parseInt(card.dataset.taskId, 10), targetColId);
    targetList.insertBefore(child, anchor.nextElementSibling);
    anchor = child;
  });
}

function moveCardAfterSubtree(card, parentCard, targetList) {
  if (!parentCard) {
    return;
  }

  let anchor = parentCard;
  let sibling = parentCard.nextElementSibling;
  while (sibling && sibling.dataset.parentTaskId === parentCard.dataset.taskId) {
    anchor = sibling;
    sibling = sibling.nextElementSibling;
  }

  targetList.insertBefore(card, anchor.nextElementSibling);
}

function promoteDraggedChildrenToRoot() {
  if (!dragState?.childElements.length) {
    return;
  }

  dragState.childElements.forEach(child => {
    const columnId = parseInt(child.dataset.columnId || dragState.sourceColId, 10);
    setTaskAsRoot(child, columnId);
  });
}

function shouldKeepExistingParent(card, targetList, targetColId) {
  if (!dragState?.wasSubtask || !dragState.sourceParentTaskId || targetColId !== dragState.sourceColId) {
    return false;
  }

  const previousCard = card.previousElementSibling;
  if (!previousCard || !previousCard.classList.contains('task-card') || !previousCard.dataset.taskId) {
    return false;
  }

  return previousCard.dataset.taskId === String(dragState.sourceParentTaskId)
    || previousCard.dataset.parentTaskId === String(dragState.sourceParentTaskId)
    || targetList.querySelector(`.task-card[data-task-id="${dragState.sourceParentTaskId}"]`) === previousCard;
}

function getDirectSubtaskElements(parentCard) {
  if (parentCard.dataset.parentTaskId) {
    return [];
  }

  const children = [];
  let sibling = parentCard.nextElementSibling;
  while (sibling && sibling.dataset.parentTaskId === parentCard.dataset.taskId) {
    children.push(sibling);
    sibling = sibling.nextElementSibling;
  }
  return children;
}

function getTaskIndex(card) {
  return [...card.parentElement.querySelectorAll('.task-card[data-task-id]')].indexOf(card);
}

function setTaskAsSubtask(card, parentTaskId, columnId) {
  card.dataset.parentTaskId = String(parentTaskId);
  card.dataset.columnId = String(columnId);
  card.classList.add('task-card-subtask');
  syncTaskCardPresentation(card);
}

function setTaskAsRoot(card, columnId) {
  card.dataset.parentTaskId = '';
  card.dataset.columnId = String(columnId);
  card.classList.remove('task-card-subtask');
  syncTaskCardPresentation(card);
}

function syncTaskCardPresentation(card) {
  syncTaskCardCompletedClass(card);
  syncTaskCompletionControl(card);
  syncTaskCardMeta(card);
  syncTaskAddSubtaskButton(card);
}

function syncTaskCardCompletedClass(card) {
  card.classList.toggle('task-card-completed', card.dataset.completed === 'true');
}

function syncTaskCompletionControl(card) {
  const main = card.querySelector('.task-main');
  if (!main) {
    return;
  }

  let button = main.querySelector('.subtask-complete-btn');
  if (!card.dataset.parentTaskId) {
    button?.remove();
    return;
  }

  if (!button) {
    button = document.createElement('button');
    button.className = 'subtask-complete-btn';
    button.type = 'button';
    button.title = 'Marcar subtarea como completada';
    button.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"></polyline></svg>';
    bindSubtaskCompletionButton(button);
    main.insertBefore(button, main.firstChild);
  }

  button.dataset.taskId = card.dataset.taskId;
  applySubtaskCompletionVisual(button, card.dataset.completed === 'true');
}

function applySubtaskCompletionVisual(button, completed) {
  button.classList.toggle('is-checked', completed);
  button.setAttribute('aria-pressed', completed ? 'true' : 'false');
  button.title = completed ? 'Marcar subtarea como pendiente' : 'Marcar subtarea como completada';
  button.setAttribute('aria-label', button.title);
}

function syncTaskCardMeta(card) {
  const meta = card.querySelector('.task-meta');
  const dueDate = meta?.querySelector('.due-date') || null;
  const shouldShowLabel = !card.dataset.parentTaskId;
  const labelId = card.dataset.labelId ? parseInt(card.dataset.labelId, 10) : null;
  const label = labelId ? (window.KANDO.labels || []).find(item => item.id === labelId) : null;
  let labelChip = meta?.querySelector('.label-chip') || null;
  let nextMeta = meta;

  if (shouldShowLabel && label) {
    nextMeta = nextMeta || createTaskMetaContainer(card);
    if (!labelChip) {
      labelChip = document.createElement('span');
      labelChip.className = 'label-chip';
      if (dueDate) {
        nextMeta.insertBefore(labelChip, dueDate);
      } else {
        nextMeta.appendChild(labelChip);
      }
    }

    labelChip.textContent = label.name;
    labelChip.style.background = `${label.color}2b`;
    labelChip.style.color = label.color;
    labelChip.style.borderColor = `${label.color}40`;
  } else if (labelChip) {
    labelChip.remove();
  }

  if (nextMeta && !nextMeta.querySelector('.label-chip') && !nextMeta.querySelector('.due-date')) {
    nextMeta.remove();
  }
}

function createTaskMetaContainer(card) {
  const meta = document.createElement('div');
  meta.className = 'task-meta';
  const referenceNode = card.querySelector('.task-add-subtask') || card.querySelector('.task-delete-btn');
  if (referenceNode) {
    card.insertBefore(meta, referenceNode);
  } else {
    card.appendChild(meta);
  }
  return meta;
}

function syncTaskAddSubtaskButton(card) {
  let button = card.querySelector('.task-add-subtask');
  if (card.dataset.parentTaskId) {
    button?.remove();
    return;
  }

  if (!button) {
    button = document.createElement('button');
    button.className = 'task-add-subtask';
    button.type = 'button';
    button.title = 'Crear subtarea';
    button.textContent = '+';
    bindAddSubtaskButton(button);
    card.insertBefore(button, card.querySelector('.task-delete-btn'));
  }

  button.dataset.taskId = card.dataset.taskId;
  button.dataset.columnId = card.dataset.columnId;
  if (card.dataset.labelId) {
    button.dataset.labelId = card.dataset.labelId;
  } else {
    delete button.dataset.labelId;
  }
}

function bindAddSubtaskButton(button) {
  if (button.dataset.boundAddSubtask === 'true') {
    return;
  }

  button.dataset.boundAddSubtask = 'true';
  button.addEventListener('click', event => {
    event.stopPropagation();
    if (Date.now() < suppressTaskClickUntil) return;
    openInlineSubtaskComposer(event.currentTarget);
  });
}

function bindSubtaskCompletionButton(button) {
  if (button.dataset.boundSubtaskCompletion === 'true') {
    return;
  }

  button.dataset.boundSubtaskCompletion = 'true';
  button.addEventListener('click', event => {
    event.stopPropagation();
    if (Date.now() < suppressTaskClickUntil) return;
    const taskId = parseInt(event.currentTarget.dataset.taskId, 10);
    const card = event.currentTarget.closest('.task-card');
    const nextCompleted = !(card?.dataset.completed === 'true');
    updateTaskCompletion(taskId, nextCompleted, { preserveModal: true }).catch(() => {});
  });
}

function clearTaskDropTargets() {
  document.querySelectorAll('.task-card-drop-target').forEach(card => {
    card.classList.remove('task-card-drop-target');
  });
}

/* ── Edit mode (column drag) ──────────────────────────────────────────────── */
function toggleEditMode() {
  editMode = !editMode;
  const menuBtn = document.getElementById('btnEditMode');
  if (menuBtn) menuBtn.style.display = editMode ? 'none' : '';
  const doneBtn = document.getElementById('btnEditModeDone');
  if (doneBtn) doneBtn.style.display = editMode ? 'inline-flex' : 'none';

  document.querySelectorAll('.column-edit-actions').forEach(el => {
    el.style.display = editMode ? 'flex' : 'none';
  });
  document.getElementById('addColumnBtn').style.display = editMode ? 'flex' : 'none';

  document.getElementById('board')?.classList.toggle('board-edit-mode', editMode);

  if (editMode) {
    columnSortable = Sortable.create(document.getElementById('board'), {
      animation: 160,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      handle: '.column-header',
      filter: '.btn-icon, .add-column-btn',
      preventOnFilter: true,
      onEnd() {
        persistColumnOrder();
      }
    });
  } else if (columnSortable) {
    columnSortable.destroy();
    columnSortable = null;
  }
}

function persistColumnOrder() {
  const ids = [...document.querySelectorAll('.column[data-col-id]')]
    .map(column => parseInt(column.dataset.colId, 10));
  api('POST', '/api/columns/reorder', ids);
}

/* ── Input modal helper ───────────────────────────────────────────────────── */
let _inputModalResolve = null;

function showInputModal(title, defaultValue = '') {
  document.getElementById('inputModalTitle').textContent = title;
  const field = document.getElementById('inputModalField');
  field.value = defaultValue;
  document.getElementById('inputModal').style.display = 'flex';
  setTimeout(() => { field.focus(); field.select(); }, 50);
  return new Promise(resolve => { _inputModalResolve = resolve; });
}

function confirmInputModal() {
  const val = document.getElementById('inputModalField').value.trim();
  closeInputModal(val || null);
}

function cancelInputModal() {
  closeInputModal(null);
}

function closeInputModal(value) {
  document.getElementById('inputModal').style.display = 'none';
  if (_inputModalResolve) { _inputModalResolve(value); _inputModalResolve = null; }
}

function inputModalOutsideClick(event) {
  if (event.target === document.getElementById('inputModal')) cancelInputModal();
}

document.addEventListener('keydown', e => {
  if (document.getElementById('inputModal').style.display !== 'none') {
    if (e.key === 'Enter') confirmInputModal();
    if (e.key === 'Escape') cancelInputModal();
  }
});

/* ── Column operations ────────────────────────────────────────────────────── */
function addColumn() {
  showInputModal('Nombre de la columna').then(name => {
    if (!name) return;
    api('POST', '/api/columns', { name }).then(() => location.reload());
  });
}

function renameColumn(btn) {
  const colId = btn.dataset.colId;
  const column = document.querySelector(`.column[data-col-id="${colId}"]`);
  const title = column.querySelector('.column-title');
  const current = title.textContent;
  showInputModal('Nuevo nombre', current).then(name => {
    if (!name || name === current) return;
    api('PUT', `/api/columns/${colId}`, { name }).then(() => {
      title.textContent = name;
    });
  });
}

function deleteColumn(btn) {
  const colId = btn.dataset.colId;
  if (!confirm('¿Eliminar esta columna y todas sus tareas?')) {
    return;
  }
  api('DELETE', `/api/columns/${colId}`).then(() => {
    document.querySelector(`.column[data-col-id="${colId}"]`).remove();
  });
}

function sortColumnByLabel(btn) {
  const colId = parseInt(btn.dataset.colId, 10);
  if (!colId || btn.disabled) {
    return;
  }

  const currentDirection = btn.dataset.sortDirection || SORT_DIRECTION_NONE;
  const nextDirection = currentDirection === SORT_DIRECTION_ASC ? SORT_DIRECTION_DESC : SORT_DIRECTION_ASC;
  btn.disabled = true;
  api('POST', `/api/columns/${colId}/sort-by-label?direction=${nextDirection}`)
    .then(() => {
      saveColumnSortDirection(colId, nextDirection);
      applyColumnSortButtonState(btn, nextDirection);
      location.reload();
    })
    .catch(async error => {
      btn.disabled = false;
      alert(await extractApiErrorMessage(error, 'No he podido ordenar la columna por etiqueta.'));
    });
}

function syncSortButtons() {
  document.querySelectorAll('.column-sort-btn[data-col-id]').forEach(button => {
    const colId = parseInt(button.dataset.colId, 10);
    applyColumnSortButtonState(button, loadColumnSortDirection(colId));
  });
}

function loadColumnSortDirection(columnId) {
  try {
    const directions = JSON.parse(localStorage.getItem(COLUMN_SORT_STORAGE_KEY) || '{}');
    return directions[columnId] || SORT_DIRECTION_NONE;
  } catch (_error) {
    return SORT_DIRECTION_NONE;
  }
}

function saveColumnSortDirection(columnId, direction) {
  try {
    const directions = JSON.parse(localStorage.getItem(COLUMN_SORT_STORAGE_KEY) || '{}');
    directions[columnId] = direction;
    localStorage.setItem(COLUMN_SORT_STORAGE_KEY, JSON.stringify(directions));
  } catch (_error) {
    // Ignore storage failures and keep the board usable.
  }
}

function applyColumnSortButtonState(button, direction) {
  const normalizedDirection = direction === SORT_DIRECTION_DESC ? SORT_DIRECTION_DESC
    : direction === SORT_DIRECTION_ASC ? SORT_DIRECTION_ASC
      : SORT_DIRECTION_NONE;
  const text = normalizedDirection === SORT_DIRECTION_DESC ? 'Z-A' : 'A-Z';
  const title = normalizedDirection === SORT_DIRECTION_DESC
    ? 'Ordenado por etiqueta Z-A. Pulsar para A-Z.'
    : normalizedDirection === SORT_DIRECTION_ASC
      ? 'Ordenado por etiqueta A-Z. Pulsar para Z-A.'
      : 'Ordenar por etiqueta A-Z.';

  button.dataset.sortDirection = normalizedDirection;
  button.classList.toggle('is-active', normalizedDirection !== SORT_DIRECTION_NONE);
  button.classList.toggle('is-desc', normalizedDirection === SORT_DIRECTION_DESC);
  button.querySelector('.column-sort-copy').textContent = text;
  button.title = title;
  button.setAttribute('aria-label', title);
}

/* ── Board filters ────────────────────────────────────────────────────────── */
function bindBoardFilters() {
  const searchInput = document.getElementById('boardSearchInput');
  const searchClearButton = document.getElementById('boardSearchClearBtn');
  const filter = document.getElementById('boardLabelFilter');
  const filterButton = document.getElementById('boardLabelFilterBtn');
  const filterMenu = document.getElementById('boardLabelFilterMenu');
  const filterSearch = document.getElementById('boardLabelFilterSearch');
  if (!searchInput || !searchClearButton || !filter || !filterButton || !filterMenu || !filterSearch) {
    return;
  }

  searchInput.addEventListener('input', event => {
    boardSearchTerm = normalizeBoardFilterText(event.currentTarget.value);
    syncBoardSearchClearButton();
    applyBoardFilters();
  });

  searchClearButton.addEventListener('click', event => {
    event.preventDefault();
    event.stopPropagation();
    clearBoardSearchFilter();
  });

  filterButton.addEventListener('click', event => {
    if (event.target.closest('.board-filter-chip-remove')) {
      return;
    }
    event.stopPropagation();
    toggleBoardLabelFilterMenu();
  });

  filterButton.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      closeBoardLabelFilterMenu(true);
      return;
    }

    if (event.key !== 'Enter' && event.key !== ' ' && event.key !== 'ArrowDown') {
      return;
    }

    event.preventDefault();
    toggleBoardLabelFilterMenu(true);
  });

  filterSearch.addEventListener('input', renderBoardLabelFilterOptions);
  filterSearch.addEventListener('keydown', event => {
    if (event.key !== 'Escape') {
      return;
    }

    event.preventDefault();
    closeBoardLabelFilterMenu(true);
    filterButton.focus();
  });

  filterMenu.addEventListener('click', event => event.stopPropagation());
  document.addEventListener('click', event => {
    if (!filter.contains(event.target)) {
      closeBoardLabelFilterMenu();
    }
  });

  syncBoardSearchClearButton();
  syncBoardLabelFilterText();
  renderBoardLabelFilterOptions();
  applyBoardFilters();
}

function normalizeBoardFilterText(value) {
  return (value || '').trim().toLowerCase();
}

function toggleBoardLabelFilterMenu(forceOpen = null) {
  const filter = document.getElementById('boardLabelFilter');
  const menu = document.getElementById('boardLabelFilterMenu');
  const button = document.getElementById('boardLabelFilterBtn');
  const shouldOpen = forceOpen == null ? !filter?.classList.contains('is-open') : forceOpen;

  if (!filter || !menu || !button) {
    return;
  }

  filter.classList.toggle('is-open', shouldOpen);
  menu.style.display = shouldOpen ? 'block' : 'none';
  button.setAttribute('aria-expanded', shouldOpen ? 'true' : 'false');
  if (!shouldOpen) {
    return;
  }

  renderBoardLabelFilterOptions();
  requestAnimationFrame(() => document.getElementById('boardLabelFilterSearch')?.focus());
}

function closeBoardLabelFilterMenu(restoreFocus = false) {
  const filter = document.getElementById('boardLabelFilter');
  const menu = document.getElementById('boardLabelFilterMenu');
  const button = document.getElementById('boardLabelFilterBtn');
  const search = document.getElementById('boardLabelFilterSearch');
  if (!filter || !menu || !button) {
    return;
  }

  filter.classList.remove('is-open');
  menu.style.display = 'none';
  button.setAttribute('aria-expanded', 'false');
  if (search) {
    search.value = '';
  }
  if (restoreFocus) {
    button.focus();
  }
}

function clearBoardSearchFilter() {
  const searchInput = document.getElementById('boardSearchInput');
  if (!searchInput) {
    return;
  }

  searchInput.value = '';
  boardSearchTerm = '';
  syncBoardSearchClearButton();
  applyBoardFilters();
  searchInput.focus();
}

function syncBoardSearchClearButton() {
  const searchInput = document.getElementById('boardSearchInput');
  const searchClearButton = document.getElementById('boardSearchClearBtn');
  if (!searchInput || !searchClearButton) {
    return;
  }

  searchClearButton.hidden = !searchInput.value.trim();
}

function renderBoardLabelFilterOptions() {
  const options = document.getElementById('boardLabelFilterOptions');
  const search = document.getElementById('boardLabelFilterSearch');
  if (!options || !search) {
    return;
  }

  const labels = [...(window.KANDO.labels || [])]
    .sort((first, second) => first.name.localeCompare(second.name, 'es', { sensitivity: 'base' }));
  const searchTerm = normalizeBoardFilterText(search.value);
  const matchingLabels = labels.filter(label => !searchTerm || label.name.toLowerCase().includes(searchTerm));

  options.innerHTML = '';
  options.appendChild(buildBoardLabelFilterOption(null));

  if (!matchingLabels.length) {
    const empty = document.createElement('div');
    empty.className = 'board-label-filter-empty';
    empty.textContent = 'No he encontrado etiquetas.';
    options.appendChild(empty);
    return;
  }

  matchingLabels.forEach(label => {
    options.appendChild(buildBoardLabelFilterOption(label));
  });
}

function buildBoardLabelFilterOption(label) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'board-label-filter-option';
  button.setAttribute('role', 'option');

  const active = label == null ? boardFilterLabelId == null : boardFilterLabelId === label.id;
  button.classList.toggle('is-active', active);
  button.setAttribute('aria-selected', active ? 'true' : 'false');

  if (label) {
    const dot = document.createElement('span');
    dot.className = 'board-label-filter-option-dot';
    dot.style.background = label.color;
    button.appendChild(dot);
  }

  const text = document.createElement('span');
  text.textContent = label?.name || 'Todas las etiquetas';
  button.appendChild(text);

  button.addEventListener('click', () => {
    boardFilterLabelId = label?.id || null;
    syncBoardLabelFilterText();
    renderBoardLabelFilterOptions();
    applyBoardFilters();
    closeBoardLabelFilterMenu();
  });

  return button;
}

function syncBoardLabelFilterText() {
  const value = document.getElementById('boardLabelFilterValue');
  if (!value) {
    return;
  }

  const label = (window.KANDO.labels || []).find(item => item.id === boardFilterLabelId) || null;
  if (!label) {
    boardFilterLabelId = null;
  }
  value.innerHTML = '';

  if (!label) {
    const text = document.createElement('span');
    text.id = 'boardLabelFilterText';
    text.textContent = 'Etiqueta';
    value.appendChild(text);
    return;
  }

  const chip = document.createElement('span');
  chip.className = 'board-filter-chip';
  chip.style.background = `${label.color}22`;
  chip.style.color = label.color;
  chip.style.borderColor = `${label.color}40`;

  const chipLabel = document.createElement('span');
  chipLabel.className = 'board-filter-chip-label';
  chipLabel.textContent = label.name;

  const remove = document.createElement('button');
  remove.type = 'button';
  remove.className = 'board-filter-chip-remove';
  remove.title = 'Quitar filtro de etiqueta';
  remove.setAttribute('aria-label', 'Quitar filtro de etiqueta');
  remove.textContent = '×';
  remove.addEventListener('click', event => {
    event.preventDefault();
    event.stopPropagation();
    clearBoardLabelFilter();
  });

  chip.append(chipLabel, remove);
  value.appendChild(chip);
}

function clearBoardLabelFilter() {
  boardFilterLabelId = null;
  syncBoardLabelFilterText();
  renderBoardLabelFilterOptions();
  applyBoardFilters();
  closeBoardLabelFilterMenu();
}

function applyBoardFilters() {
  document.querySelectorAll('.task-list').forEach(list => {
    const cards = [...list.querySelectorAll('.task-card[data-task-id]')];
    const rootCards = cards.filter(card => !card.dataset.parentTaskId);

    rootCards.forEach(rootCard => {
      const blockCards = [rootCard, ...getDirectSubtaskElements(rootCard)];
      const matchesTitle = !boardSearchTerm || blockCards.some(card => {
        const title = card.querySelector('.task-title')?.textContent || '';
        return title.toLowerCase().includes(boardSearchTerm);
      });
      const matchesLabel = !boardFilterLabelId || blockCards.some(card => {
        return card.dataset.labelId === String(boardFilterLabelId);
      });
      const isVisible = matchesTitle && matchesLabel;

      blockCards.forEach(card => {
        card.style.display = isVisible ? '' : 'none';
      });
    });

    list.querySelectorAll('.quick-add-card[data-parent-task-id]').forEach(draft => {
      const parentCard = list.querySelector(`.task-card[data-task-id="${draft.dataset.parentTaskId}"]`);
      draft.style.display = parentCard?.style.display === 'none' ? 'none' : '';
    });
  });

  syncBoardCentering();
}

/* ── Quick add ────────────────────────────────────────────────────────────── */
function bindQuickAddInput(input) {
  if (!input || input.dataset.quickBound === 'true') {
    return;
  }

  input.dataset.quickBound = 'true';
  input.addEventListener('input', () => clearQuickAddState(input));
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      submitQuickAddInput(input);
      return;
    }

    if (event.key === 'Escape' && isInlineSubtaskInput(input)) {
      event.preventDefault();
      removeInlineSubtaskDraft(input.closest('.quick-add-card'));
    }
  });

  if (isInlineSubtaskInput(input)) {
    input.addEventListener('blur', () => {
      window.setTimeout(() => cleanupInlineSubtaskDraft(input), 0);
    });
  }
}

function bindQuickAddCard(card) {
  if (!card || card.dataset.quickCardBound === 'true') {
    return;
  }

  card.dataset.quickCardBound = 'true';
  card.addEventListener('click', event => {
    if (event.target.closest('.quick-add-input')) {
      return;
    }
    card.querySelector('.quick-add-input')?.focus();
  });
}

function submitQuickAddInput(input) {
  if (isInlineSubtaskInput(input)) {
    quickAddSubtaskFromInput(input);
    return;
  }

  quickAddFromInput(input);
}

function isInlineSubtaskInput(input) {
  return Boolean(input?.closest('.quick-add-card')?.dataset.parentTaskId);
}

function normalizeQuickAddTitle(title) {
  return title.replace(QUICK_LABEL_PATTERN_GLOBAL, '').replace(/\s{2,}/g, ' ').trim();
}

function quickAddFromInput(input) {
  const title = input.value.trim();
  if (!title) {
    clearQuickAddState(input);
    return;
  }

  const validation = validateQuickAddTitle(title);
  if (!validation.valid) {
    setQuickAddState(input, validation.message);
    return;
  }

  const colId = parseInt(input.dataset.colId, 10);
  clearQuickAddState(input);
  api('POST', '/api/tasks/quick', { title, columnId: colId })
    .then(() => location.reload())
    .catch(async error => {
      setQuickAddState(input, await extractApiErrorMessage(error, 'No he podido crear la tarea.'));
    });
}

function validateQuickAddTitle(title) {
  if (!QUICK_LABEL_PATTERN.test(title)) {
    return {
      valid: false,
      message: 'Añade una #etiqueta existente para crear la tarea.'
    };
  }

  const plainTitle = normalizeQuickAddTitle(title);
  if (!plainTitle) {
    return {
      valid: false,
      message: 'Escribe un título además de la etiqueta.'
    };
  }

  return { valid: true };
}

function quickAddSubtaskFromInput(input) {
  const card = input.closest('.quick-add-card');
  if (!card) {
    return;
  }

  const title = input.value.trim();
  const normalizedTitle = normalizeQuickAddTitle(title);
  const columnId = parseInt(card?.dataset.colId || 0, 10);
  const parentTaskId = parseInt(card?.dataset.parentTaskId || 0, 10);
  const labelId = card?.dataset.labelId ? parseInt(card.dataset.labelId, 10) : null;

  if (!title) {
    removeInlineSubtaskDraft(card);
    return;
  }

  if (!normalizedTitle) {
    setQuickAddState(input, 'Escribe un título para la subtarea.');
    return;
  }

  if (!labelId && !QUICK_LABEL_PATTERN.test(title)) {
    setQuickAddState(input, 'Añade una #etiqueta o crea la subtarea desde una tarea con etiqueta.');
    return;
  }

  clearQuickAddState(input);
  api('POST', '/api/tasks/quick', { title, columnId, labelId })
    .then(task => api('PUT', `/api/tasks/${task.id}`, {
      title: normalizedTitle,
      notes: null,
      dueDate: null,
      labelId,
      columnId,
      parentTaskId
    }))
    .then(() => location.reload())
    .catch(async error => {
      setQuickAddState(input, await extractApiErrorMessage(error, 'No he podido crear la subtarea.'));
    });
}

function openInlineSubtaskComposer(button) {
  const parentTaskId = parseInt(button.dataset.taskId, 10);
  const existingDraft = document.querySelector(`.quick-add-card[data-parent-task-id="${parentTaskId}"]`);
  if (existingDraft) {
    existingDraft.querySelector('.quick-add-input')?.focus();
    return;
  }

  closeInlineSubtaskDrafts(parentTaskId);

  const draft = buildInlineSubtaskDraft(button);
  const parentCard = button.closest('.task-card');
  if (!parentCard) {
    return;
  }

  insertInlineSubtaskDraft(parentCard, draft);
  bindQuickAddCard(draft);
  bindQuickAddInput(draft.querySelector('.quick-add-input'));
  requestAnimationFrame(() => draft.querySelector('.quick-add-input')?.focus());
}

function buildInlineSubtaskDraft(button) {
  const draft = document.createElement('div');
  draft.className = 'quick-add-card quick-add-card-subtask';
  draft.dataset.colId = button.dataset.columnId;
  draft.dataset.parentTaskId = button.dataset.taskId;
  if (button.dataset.labelId) {
    draft.dataset.labelId = button.dataset.labelId;
  }

  const input = document.createElement('input');
  input.type = 'text';
  input.className = 'quick-add-input';
  input.placeholder = 'Nueva subtarea';
  input.autocomplete = 'off';

  const feedback = document.createElement('span');
  feedback.className = 'quick-add-feedback';
  feedback.setAttribute('aria-live', 'polite');

  draft.append(input, feedback);
  return draft;
}

function insertInlineSubtaskDraft(parentCard, draft) {
  const list = parentCard.closest('.task-list');
  if (!list) {
    return;
  }

  let anchor = parentCard;
  let sibling = parentCard.nextElementSibling;
  while (sibling && sibling.dataset.parentTaskId === parentCard.dataset.taskId) {
    anchor = sibling;
    sibling = sibling.nextElementSibling;
  }

  list.insertBefore(draft, anchor.nextElementSibling);
}

function closeInlineSubtaskDrafts(exceptParentTaskId = null) {
  document.querySelectorAll('.quick-add-card[data-parent-task-id]').forEach(draft => {
    if (exceptParentTaskId && draft.dataset.parentTaskId === String(exceptParentTaskId)) {
      return;
    }
    draft.remove();
  });
}

function removeInlineSubtaskDraft(draft) {
  if (!draft?.dataset.parentTaskId) {
    return;
  }
  draft.remove();
}

function cleanupInlineSubtaskDraft(input) {
  const draft = input?.closest('.quick-add-card');
  if (!draft || !draft.dataset.parentTaskId) {
    return;
  }
  if (draft.contains(document.activeElement) || input.value.trim()) {
    return;
  }
  removeInlineSubtaskDraft(draft);
}

function setQuickAddState(input, message) {
  const card = input.closest('.quick-add-card');
  const feedback = card?.querySelector('.quick-add-feedback');
  if (!card || !feedback) {
    return;
  }

  card.classList.add('is-invalid');
  feedback.textContent = message;
}

function clearQuickAddState(input) {
  const card = input.closest('.quick-add-card');
  const feedback = card?.querySelector('.quick-add-feedback');
  if (!card || !feedback) {
    return;
  }

  card.classList.remove('is-invalid');
  feedback.textContent = '';
}

/* ── Task cards ───────────────────────────────────────────────────────────── */
function bindTaskCardInteractions() {
  document.querySelectorAll('.task-card[data-task-id]').forEach(card => {
    if (card.dataset.boundCard === 'true') {
      return;
    }

    card.dataset.boundCard = 'true';
    card.addEventListener('click', event => {
      if (Date.now() < suppressTaskClickUntil) {
        event.preventDefault();
        return;
      }
      openTask(parseInt(card.dataset.taskId, 10));
    });
    card.addEventListener('keydown', event => {
      if (event.key !== 'Enter' && event.key !== ' ') {
        return;
      }
      event.preventDefault();
      openTask(parseInt(card.dataset.taskId, 10));
    });

    syncTaskCardPresentation(card);
    if (card.querySelector('.task-add-subtask')) {
      bindAddSubtaskButton(card.querySelector('.task-add-subtask'));
    }
    if (card.querySelector('.subtask-complete-btn')) {
      bindSubtaskCompletionButton(card.querySelector('.subtask-complete-btn'));
    }
    if (card.querySelector('.task-delete-btn')) {
      bindDeleteTaskButton(card.querySelector('.task-delete-btn'));
    }
  });
}

function bindDeleteTaskButton(button) {
  if (button.dataset.boundDelete === 'true') {
    return;
  }

  button.dataset.boundDelete = 'true';
  button.addEventListener('click', event => {
    event.stopPropagation();
    if (Date.now() < suppressTaskClickUntil) return;
    const taskId = parseInt(event.currentTarget.dataset.taskId, 10);
    if (!confirm('¿Eliminar esta tarea?')) return;
    api('DELETE', `/api/tasks/${taskId}`).then(() => location.reload());
  });
}

/* ── Task modal ───────────────────────────────────────────────────────────── */
function bindModalPickers() {
  document.getElementById('labelCpickerTrigger').addEventListener('click', e => {
    e.stopPropagation();
    toggleCpicker('labelCpicker');
  });
  document.getElementById('parentCpickerTrigger').addEventListener('click', e => {
    e.stopPropagation();
    toggleCpicker('parentCpicker');
  });
  document.getElementById('labelCpickerTrigger').addEventListener('keydown', event => {
    handleCpickerTriggerKeydown(event, 'labelCpicker');
  });
  document.getElementById('parentCpickerTrigger').addEventListener('keydown', event => {
    handleCpickerTriggerKeydown(event, 'parentCpicker');
  });

  document.getElementById('modalLabelSearch').addEventListener('input', renderLabelPicker);
  document.getElementById('modalParentSearch').addEventListener('input', renderParentPicker);
  document.getElementById('modalLabelSearch').addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      closeCpicker('labelCpicker');
      document.getElementById('labelCpickerTrigger')?.focus();
    }
  });
  document.getElementById('modalParentSearch').addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      closeCpicker('parentCpicker');
      document.getElementById('parentCpickerTrigger')?.focus();
    }
  });

  // Close on outside click
  document.addEventListener('click', e => {
    if (!e.target.closest('#labelCpicker'))  closeCpicker('labelCpicker');
    if (!e.target.closest('#parentCpicker')) closeCpicker('parentCpicker');
  });
}

function handleCpickerTriggerKeydown(event, pickerId) {
  if (event.key !== 'Enter' && event.key !== ' ' && event.key !== 'ArrowDown') {
    if (event.key === 'Escape') {
      closeCpicker(pickerId);
    }
    return;
  }

  event.preventDefault();
  toggleCpicker(pickerId);
}

function toggleCpicker(id) {
  const el = document.getElementById(id);
  if (!el) return;
  if (el.classList.contains('is-open')) {
    closeCpicker(id);
    return;
  }

  openCpicker(id);
}

function openCpicker(id) {
  const el = document.getElementById(id);
  if (!el) return;

  closeCpicker('labelCpicker');
  closeCpicker('parentCpicker');
  activeCpickerId = id;
  el.classList.add('is-open');
  el.querySelector('.cpicker-trigger')?.setAttribute('aria-expanded', 'true');
  el.querySelector('.cpicker-dropdown').style.display = 'flex';
  if (id === 'labelCpicker') renderLabelPicker();
  if (id === 'parentCpicker') renderParentPicker();
  syncOpenCpickerPosition();
  requestAnimationFrame(() => {
    syncOpenCpickerPosition();
    el.querySelector('.cpicker-search')?.focus();
  });
}

function closeCpicker(id) {
  const el = document.getElementById(id);
  if (!el) return;
  if (activeCpickerId === id) {
    activeCpickerId = null;
  }
  el.classList.remove('is-open');
  el.querySelector('.cpicker-trigger')?.setAttribute('aria-expanded', 'false');
  const dd = el.querySelector('.cpicker-dropdown');
  if (dd) {
    dd.style.display = 'none';
    dd.style.top = '';
    dd.style.bottom = '';
    dd.style.left = '';
    dd.style.width = '';
    dd.style.maxHeight = '';
  }
  const s = el.querySelector('.cpicker-search');
  if (s) s.value = '';
}

function syncOpenCpickerPosition() {
  if (!activeCpickerId) {
    return;
  }

  const picker = document.getElementById(activeCpickerId);
  const trigger = picker?.querySelector('.cpicker-trigger');
  const dropdown = picker?.querySelector('.cpicker-dropdown');
  const results = dropdown?.querySelector('.cpicker-results');
  if (!picker || !trigger || !dropdown || !results || !picker.classList.contains('is-open')) {
    return;
  }

  const rect = trigger.getBoundingClientRect();
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  const modalRect = picker.closest('.modal')?.getBoundingClientRect() || null;
  const desiredWidth = Math.max(PICKER_MIN_WIDTH, Math.min(PICKER_MAX_WIDTH, rect.width));
  const width = Math.min(desiredWidth, viewportWidth - PICKER_VIEWPORT_GAP * 2);
  const left = Math.max(
    PICKER_VIEWPORT_GAP,
    Math.min(rect.left, viewportWidth - width - PICKER_VIEWPORT_GAP)
  );
  const availableBelow = viewportHeight - rect.bottom - PICKER_VIEWPORT_GAP;
  const availableAbove = rect.top - PICKER_VIEWPORT_GAP;
  const modalSpaceBelow = modalRect ? modalRect.bottom - rect.bottom : availableBelow;
  const preferAboveFromModal = Boolean(modalRect) && modalSpaceBelow < 260;
  const shouldOpenAbove = preferAboveFromModal || (availableBelow < 250 && availableAbove > availableBelow);
  const panelHeight = Math.max(210, Math.min(360, (shouldOpenAbove ? availableAbove : availableBelow) - PICKER_PANEL_OFFSET));

  dropdown.style.left = `${left}px`;
  dropdown.style.width = `${width}px`;
  dropdown.style.maxHeight = `${panelHeight}px`;
  results.style.maxHeight = `${Math.max(120, panelHeight - 76)}px`;

  if (shouldOpenAbove) {
    dropdown.style.top = 'auto';
    dropdown.style.bottom = `${viewportHeight - rect.top + PICKER_PANEL_OFFSET}px`;
  } else {
    dropdown.style.top = `${Math.min(viewportHeight - PICKER_VIEWPORT_GAP, rect.bottom + PICKER_PANEL_OFFSET)}px`;
    dropdown.style.bottom = 'auto';
  }
}

function openCreateModal() {
  const firstColumn = document.querySelector('.column[data-col-id]');
  if (!firstColumn) {
    return;
  }

  closeInlineSubtaskDrafts();
  currentTaskId = null;
  currentTaskColumnId = parseInt(firstColumn.dataset.colId, 10);
  modalOriginColumnId = currentTaskColumnId;
  currentParentTaskId = null;
  currentLabelId = null;

  document.getElementById('modalTitle').textContent = 'Nueva tarea';
  document.getElementById('modalTaskTitle').value = '';
  document.getElementById('modalTaskNotes').value = '';
  document.getElementById('modalTaskDue').value = '';
  document.getElementById('modalParentSearch').value = '';
  document.getElementById('modalLabelSearch').value = '';
  toggleDeleteButton(false);
  setModalTabsVisible(false);

  renderParentPicker();
  renderLabelPicker();
  renderModalSubtasks(null);
  showModal();
}

function openTask(taskId) {
  closeInlineSubtaskDrafts();
  fetch(`/api/tasks/${taskId}`)
    .then(response => response.json())
    .then(task => {
      currentTaskId = task.id;
      currentTaskColumnId = task.columnId
        || parseInt(document.querySelector(`.task-card[data-task-id="${task.id}"]`)?.dataset.columnId || 0, 10);
      modalOriginColumnId = currentTaskColumnId;
      currentParentTaskId = task.parentTaskId || null;
      currentLabelId = task.primaryLabel?.id || task.labels?.[0]?.id || null;

      document.getElementById('modalTitle').textContent = 'Editar tarea';
      document.getElementById('modalTaskTitle').value = task.title;
      document.getElementById('modalTaskNotes').value = task.notes || '';
      document.getElementById('modalTaskDue').value = task.dueDate || '';
      document.getElementById('modalParentSearch').value = '';
      document.getElementById('modalLabelSearch').value = '';
      toggleDeleteButton(true);
      setModalTabsVisible(true);
      switchModalTab('details');

      renderParentPicker();
      renderLabelPicker();
      renderModalSubtasks(task);
      showModal();
    });
}

function showModal() {
  document.getElementById('taskModal').style.display = 'flex';
  setTimeout(() => document.getElementById('modalTaskTitle').focus(), 50);
}

function setModalTabsVisible(visible) {
  const strip = document.getElementById('modalTabStrip');
  if (strip) strip.style.display = visible ? 'flex' : 'none';
  if (!visible) {
    document.getElementById('modalBodyDetails').style.display = '';
    document.getElementById('modalBodyHistory').style.display = 'none';
  }
}

function switchModalTab(tab) {
  const isDetails = tab === 'details';
  document.getElementById('modalBodyDetails').style.display = isDetails ? '' : 'none';
  document.getElementById('modalBodyHistory').style.display = isDetails ? 'none' : '';
  document.getElementById('tabBtnDetails').classList.toggle('active', isDetails);
  document.getElementById('tabBtnHistory').classList.toggle('active', !isDetails);

  if (!isDetails && currentTaskId) {
    loadTaskHistory(currentTaskId);
  }
}

function loadTaskHistory(taskId) {
  const list = document.getElementById('taskHistoryList');
  list.innerHTML = '<p class="history-loading">Cargando…</p>';

  fetch(`/api/tasks/${taskId}/history`)
    .then(r => r.json())
    .then(entries => {
      list.innerHTML = '';
      if (!entries.length) {
        list.innerHTML = '<p class="history-empty">Sin historial de columnas.</p>';
        return;
      }
      entries.forEach((entry, i) => {
        const isLast = i === entries.length - 1;
        const isDone = entry.done === 'true';
        const isCreated = entry.eventType === HISTORY_EVENT_CREATED;
        const row = document.createElement('div');
        row.className = 'history-entry'
          + (isLast ? ' history-entry-last' : '')
          + (isDone ? ' history-entry-done' : '')
          + (isCreated ? ' history-entry-created' : '');

        const dot = document.createElement('div');
        dot.className = 'history-dot';

        const body = document.createElement('div');
        body.className = 'history-entry-body';

        const title = document.createElement('span');
        title.className = 'history-col-name';
        title.textContent = entry.columnName;

        if (isCreated) {
          const createdBadge = document.createElement('span');
          createdBadge.className = 'history-created-badge';
          createdBadge.textContent = 'Creada';
          title.appendChild(createdBadge);
        }

        if (isDone) {
          const doneBadge = document.createElement('span');
          doneBadge.className = 'history-done-badge';
          doneBadge.textContent = 'Finalizado';
          title.appendChild(doneBadge);
        }

        const date = document.createElement('span');
        date.className = 'history-date';
        date.textContent = entry.movedAt;

        body.append(title, date);
        row.append(dot, body);
        list.appendChild(row);
      });
    })
    .catch(() => { list.innerHTML = '<p class="history-empty">Error al cargar el historial.</p>'; });
}

function toggleDeleteButton(visible) {
  const button = document.getElementById('modalDeleteButton');
  if (!button) {
    return;
  }
  button.style.visibility = visible ? 'visible' : 'hidden';
  button.style.pointerEvents = visible ? 'auto' : 'none';
}

function renderParentPicker() {
  const searchTerm = document.getElementById('modalParentSearch').value.trim().toLowerCase();
  const allOptions = collectParentOptions();
  const options = allOptions
    .filter(option => !searchTerm || option.text.toLowerCase().includes(searchTerm))
    .slice(0, PARENT_RESULTS_LIMIT);

  const chips = document.getElementById('parentChips');
  const placeholder = document.getElementById('parentPlaceholder');
  const selected = allOptions.find(option => option.id === currentParentTaskId);
  chips.querySelectorAll('.cpicker-chip').forEach(c => c.remove());
  if (currentParentTaskId && selected) {
    placeholder.style.display = 'none';
    const chip = document.createElement('span');
    chip.className = 'cpicker-chip cpicker-chip-parent';
    chip.title = selected.text;
    const text = document.createElement('span');
    text.textContent = selected.title;
    const rm = document.createElement('button');
    rm.type = 'button';
    rm.className = 'cpicker-chip-remove';
    rm.textContent = '×';
    rm.addEventListener('click', event => {
      event.stopPropagation();
      setParentSelection(null, modalOriginColumnId);
    });
    chip.append(text, rm);
    chips.insertBefore(chip, null);
  } else {
    placeholder.style.display = '';
  }

  renderCpickerGroups(
    document.getElementById('modalParentResults'),
    [
      {
        title: searchTerm ? 'Coincidencias' : 'Tareas disponibles',
        options
      }
    ],
    option => {
      setParentSelection(option.id, option.columnId);
      closeCpicker('parentCpicker');
    },
    'No he encontrado ninguna tarea compatible.'
  );
  syncOpenCpickerPosition();
}

function renderLabelPicker() {
  const rawSearchTerm = document.getElementById('modalLabelSearch').value.trim();
  const searchTerm = rawSearchTerm.toLowerCase();
  const labels = window.KANDO.labels || [];
  const selectedLabel = getCurrentLabel();

  const chips = document.getElementById('labelChips');
  const placeholder = document.getElementById('labelPlaceholder');
  chips.querySelectorAll('.cpicker-chip').forEach(c => c.remove());
  if (selectedLabel) {
    placeholder.style.display = 'none';
    const chip = document.createElement('span');
    chip.className = 'cpicker-chip';
    chip.style.background = selectedLabel.color + '22';
    chip.style.color = selectedLabel.color;
    const text = document.createElement('span');
    text.textContent = selectedLabel.name;
    const rm = document.createElement('button');
    rm.type = 'button'; rm.className = 'cpicker-chip-remove'; rm.textContent = '×';
    rm.addEventListener('click', e => { e.stopPropagation(); clearLabelSelection(); });
    chip.append(text, rm);
    chips.insertBefore(chip, null);
  } else {
    placeholder.style.display = '';
  }

  renderCpickerGroups(
    document.getElementById('modalLabelResults'),
    buildLabelPickerGroups(labels, searchTerm, rawSearchTerm),
    option => {
      if (option.kind === 'create') {
        createLabelFromPicker(option.rawValue);
        return;
      }

      setLabelSelection(option.id);
      closeCpicker('labelCpicker');
    },
    searchTerm ? 'No hay etiquetas que coincidan con esa búsqueda.' : 'No hay etiquetas disponibles.'
  );
  syncOpenCpickerPosition();
}

function renderCpickerGroups(container, groups, onPick, emptyText) {
  container.innerHTML = '';
  const nonEmptyGroups = groups.filter(group => group.options.length);
  if (!nonEmptyGroups.length) {
    const el = document.createElement('div');
    el.className = 'cpicker-empty';
    el.textContent = emptyText;
    container.appendChild(el);
    return;
  }

  nonEmptyGroups.forEach(group => {
    const groupEl = document.createElement('div');
    groupEl.className = 'cpicker-group';

    if (group.title) {
      const title = document.createElement('div');
      title.className = 'cpicker-group-title';
      title.textContent = group.title;
      groupEl.appendChild(title);
    }

    group.options.forEach(option => {
      groupEl.appendChild(buildCpickerOption(option, onPick));
    });

    container.appendChild(groupEl);
  });
}

function buildCpickerOption(option, onPick) {
  const row = document.createElement('button');
  row.type = 'button';
  row.className = 'cpicker-option';

  if (option.color) {
    const dot = document.createElement('span');
    dot.className = 'cpicker-option-dot';
    dot.style.background = option.color;
    row.appendChild(dot);
  }

  const copy = document.createElement('div');
  copy.className = 'cpicker-option-copy';

  const title = document.createElement('span');
  title.className = 'cpicker-option-title';
  title.textContent = option.title || option.text;
  copy.appendChild(title);

  if (option.meta) {
    const meta = document.createElement('span');
    meta.className = 'cpicker-option-meta';
    meta.textContent = option.meta;
    copy.appendChild(meta);
  }

  row.appendChild(copy);

  if (option.action) {
    const action = document.createElement('span');
    action.className = 'cpicker-option-action';
    action.textContent = option.action;
    row.appendChild(action);
  }

  row.addEventListener('click', () => onPick(option));
  return row;
}

function buildLabelPickerGroups(labels, searchTerm, rawSearchTerm) {
  const matchingLabels = labels
    .filter(label => !searchTerm || label.name.toLowerCase().includes(searchTerm))
    .slice(0, LABEL_RESULTS_LIMIT);
  const recentIds = collectRecentLabelIds();
  const recentIdSet = new Set(recentIds);
  const recentLabels = matchingLabels
    .filter(label => recentIdSet.has(label.id))
    .slice(0, RECENT_LABEL_LIMIT);
  const recentLabelIdSet = new Set(recentLabels.map(label => label.id));
  const remainingLabels = matchingLabels.filter(label => !recentLabelIdSet.has(label.id));
  const groups = [];

  if (recentLabels.length) {
    groups.push({
      title: 'Etiquetas recientes',
      options: recentLabels.map(label => ({
        id: label.id,
        title: label.name,
        color: label.color
      }))
    });
  }

  if (remainingLabels.length) {
    groups.push({
      title: recentLabels.length ? 'Todas las etiquetas' : (searchTerm ? 'Coincidencias' : 'Etiquetas'),
      options: remainingLabels.map(label => ({
        id: label.id,
        title: label.name,
        color: label.color
      }))
    });
  }

  if (rawSearchTerm && !labels.some(label => label.name.toLowerCase() === searchTerm)) {
    groups.push({
      title: '',
      options: [{
        kind: 'create',
        title: rawSearchTerm,
        meta: 'Crear etiqueta nueva',
        action: 'Nueva',
        rawValue: rawSearchTerm
      }]
    });
  }

  return groups;
}

function collectRecentLabelIds() {
  const counts = new Map();

  document.querySelectorAll('.task-card[data-task-id][data-label-id]').forEach(card => {
    const labelId = card.dataset.labelId ? parseInt(card.dataset.labelId, 10) : null;
    if (!labelId) {
      return;
    }

    counts.set(labelId, (counts.get(labelId) || 0) + 1);
  });

  const orderedIds = [...counts.entries()]
    .sort((first, second) => second[1] - first[1])
    .map(([labelId]) => labelId);

  if (orderedIds.length) {
    return orderedIds.slice(0, RECENT_LABEL_LIMIT);
  }

  return (window.KANDO.labels || [])
    .slice(0, RECENT_LABEL_LIMIT)
    .map(label => label.id);
}

function collectParentOptions() {
  return [...document.querySelectorAll('.task-card[data-task-id]')]
    .filter(card => !card.dataset.parentTaskId)
    .filter(card => parseInt(card.dataset.taskId, 10) !== currentTaskId)
    .filter(card => {
      if (!currentLabelId) return true;
      const cardLabelId = card.dataset.labelId ? parseInt(card.dataset.labelId, 10) : null;
      return cardLabelId === currentLabelId;
    })
    .map(card => {
      const column = card.closest('.column');
      const columnName = column.querySelector('.column-title').textContent.trim();
      const title = card.querySelector('.task-title').textContent.trim();
      return {
        id: parseInt(card.dataset.taskId, 10),
        columnId: parseInt(card.dataset.columnId, 10),
        labelId: card.dataset.labelId ? parseInt(card.dataset.labelId, 10) : null,
        title,
        text: `${columnName} · ${title}`,
        meta: columnName
      };
    });
}

function setParentSelection(parentTaskId, columnId) {
  currentParentTaskId = parentTaskId;
  currentTaskColumnId = parentTaskId ? columnId : modalOriginColumnId;
  if (currentParentTaskId) {
    const selectedParent = collectParentOptions().find(option => option.id === currentParentTaskId);
    currentLabelId = selectedParent?.labelId || null;
    renderLabelPicker();
  }
  renderParentPicker();
}

function setLabelSelection(labelId) {
  currentLabelId = labelId;
  if (currentParentTaskId) {
    const selectedParent = collectParentOptions().find(option => option.id === currentParentTaskId);
    if (!selectedParent || selectedParent.labelId !== currentLabelId) {
      currentParentTaskId = null;
      currentTaskColumnId = modalOriginColumnId;
    }
  }
  renderParentPicker();
  renderLabelPicker();
}

function clearLabelSelection() {
  setLabelSelection(null);
}

function getCurrentLabel() {
  return (window.KANDO.labels || []).find(label => label.id === currentLabelId) || null;
}

function createLabelFromPicker(name) {
  const labelName = name.trim();
  if (!labelName) {
    return;
  }

  api('POST', '/api/labels', { name: labelName, color: DEFAULT_LABEL_COLOR })
    .then(label => {
      window.KANDO.labels.push(label);
      setLabelSelection(label.id);
      refreshBoardLabelUi();
      closeCpicker('labelCpicker');
    })
    .catch(async error => {
      alert(await extractApiErrorMessage(error, 'No he podido crear la etiqueta.'));
    });
}

function saveTask() {
  const title = document.getElementById('modalTaskTitle').value.trim();
  if (!title) {
    document.getElementById('modalTaskTitle').focus();
    return;
  }

  if (!currentTaskId && !currentLabelId) {
    highlightRequiredLabelPicker();
    return;
  }

  const notes = document.getElementById('modalTaskNotes').value || null;
  const dueDate = document.getElementById('modalTaskDue').value || null;

  if (currentTaskId) {
    api('PUT', `/api/tasks/${currentTaskId}`, {
      title,
      notes,
      dueDate,
      labelId: currentLabelId,
      columnId: currentTaskColumnId,
      parentTaskId: currentParentTaskId
    }).then(() => location.reload())
      .catch(async error => {
        alert(await extractApiErrorMessage(error, 'No he podido guardar la tarea.'));
      });
    return;
  }

  api('POST', '/api/tasks/quick', { title, columnId: currentTaskColumnId, labelId: currentLabelId })
    .then(task => api('PUT', `/api/tasks/${task.id}`, {
      title,
      notes,
      dueDate,
      labelId: currentLabelId,
      columnId: currentTaskColumnId,
      parentTaskId: currentParentTaskId
    }))
    .then(() => location.reload())
    .catch(async error => {
      alert(await extractApiErrorMessage(error, 'No he podido crear la tarea.'));
    });
}

function deleteCurrentTask() {
  if (!currentTaskId) {
    return;
  }
  if (!confirm('¿Eliminar esta tarea?')) {
    return;
  }
  api('DELETE', `/api/tasks/${currentTaskId}`).then(() => location.reload());
}

function closeModal() {
  closeCpicker('labelCpicker');
  closeCpicker('parentCpicker');
  renderModalSubtasks(null);
  document.getElementById('taskModal').style.display = 'none';
  setModalTabsVisible(false);
  currentTaskId = null;
  currentTaskColumnId = null;
  modalOriginColumnId = null;
  currentParentTaskId = null;
  currentLabelId = null;
  toggleDeleteButton(false);
}

function closeModalOutside(event) {
  if (event.target === document.getElementById('taskModal')) {
    closeModal();
  }
}

/* ── Helpers ──────────────────────────────────────────────────────────────── */
function highlightRequiredLabelPicker() {
  const field = document.querySelector('.cpicker-field[data-picker="label"]');
  if (!field) {
    openCpicker('labelCpicker');
    return;
  }

  field.classList.add('cpicker-field-required');
  openCpicker('labelCpicker');

  window.clearTimeout(highlightRequiredLabelPicker.timeoutId);
  highlightRequiredLabelPicker.timeoutId = window.setTimeout(() => {
    field.classList.remove('cpicker-field-required');
  }, 1800);
}

function updateTaskCompletion(taskId, completed, options = {}) {
  return api('PUT', `/api/tasks/${taskId}/completion`, { completed })
    .then(task => {
      syncTaskCompletionState(taskId, task?.completed ?? completed, options);
      return task;
    })
    .catch(async error => {
      if (!options.silent) {
        alert(await extractApiErrorMessage(error, 'No he podido actualizar la subtarea.'));
      }
      throw error;
    });
}

function syncTaskCompletionState(taskId, completed, options = {}) {
  const card = document.querySelector(`.task-card[data-task-id="${taskId}"]`);
  if (card) {
    card.dataset.completed = String(completed);
    syncTaskCardPresentation(card);
  }

  if (options.preserveModal && document.getElementById('taskModal')?.style.display !== 'none') {
    renderOpenTaskSubtasks();
  }
}

function renderOpenTaskSubtasks() {
  if (!currentTaskId) {
    renderModalSubtasks(null);
    return;
  }

  const currentCard = document.querySelector(`.task-card[data-task-id="${currentTaskId}"]`);
  renderModalSubtasks({
    id: currentTaskId,
    parentTaskId: currentCard?.dataset.parentTaskId ? parseInt(currentCard.dataset.parentTaskId, 10) : null
  });
}

function renderModalSubtasks(task) {
  const field = document.getElementById('modalSubtasksField');
  const list = document.getElementById('modalSubtasksList');
  if (!field || !list) {
    return;
  }

  if (!task || task.parentTaskId) {
    field.style.display = 'none';
    list.innerHTML = '';
    return;
  }

  const subtasks = findDirectSubtaskCards(task.id);
  field.style.display = '';
  list.innerHTML = '';

  if (!subtasks.length) {
    const empty = document.createElement('p');
    empty.className = 'modal-subtasks-empty';
    empty.textContent = 'Sin subtareas todavía.';
    list.appendChild(empty);
    return;
  }

  subtasks.forEach(card => {
    const item = document.createElement('div');
    item.className = 'modal-subtask-item';
    item.classList.toggle('is-checked', card.dataset.completed === 'true');

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'modal-subtask-toggle';
    toggle.innerHTML = '<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"></polyline></svg>';
    applyModalSubtaskToggleVisual(toggle, card.dataset.completed === 'true');

    toggle.addEventListener('click', event => {
      const nextCompleted = !toggle.classList.contains('is-checked');
      item.classList.toggle('is-checked', nextCompleted);
      applyModalSubtaskToggleVisual(toggle, nextCompleted);
      event.currentTarget.disabled = true;
      updateTaskCompletion(parseInt(card.dataset.taskId, 10), nextCompleted, { preserveModal: true })
        .catch(() => {
          item.classList.toggle('is-checked', !nextCompleted);
          applyModalSubtaskToggleVisual(toggle, !nextCompleted);
        })
        .finally(() => {
          event.currentTarget.disabled = false;
        });
    });

    const title = document.createElement('span');
    title.className = 'modal-subtask-title';
    title.textContent = card.querySelector('.task-title')?.textContent?.trim() || 'Subtarea';

    item.append(toggle, title);
    list.appendChild(item);
  });
}

function applyModalSubtaskToggleVisual(toggle, completed) {
  toggle.classList.toggle('is-checked', completed);
  toggle.title = completed ? 'Marcar subtarea como pendiente' : 'Marcar subtarea como completada';
  toggle.setAttribute('aria-label', toggle.title);
}

function findDirectSubtaskCards(taskId) {
  const parentCard = document.querySelector(`.task-card[data-task-id="${taskId}"]`);
  return parentCard ? getDirectSubtaskElements(parentCard) : [];
}

async function extractApiErrorMessage(error, fallbackMessage) {
  if (!(error instanceof Response)) {
    return fallbackMessage;
  }

  const contentType = error.headers.get('content-type') || '';

  try {
    if (contentType.includes('application/json')) {
      const payload = await error.json();
      if (typeof payload?.message === 'string' && payload.message.trim()) {
        return payload.message.trim();
      }
    } else {
      const text = await error.text();
      if (text.trim() && !text.trim().startsWith('<')) {
        return text.trim();
      }
    }
  } catch (_ignored) {
    return fallbackMessage;
  }

  return fallbackMessage;
}

function api(method, url, body) {
  const options = {
    method,
    headers: { 'Content-Type': 'application/json' }
  };

  if (body !== undefined) {
    options.body = JSON.stringify(body);
  }

  return fetch(url, options).then(async response => {
    if (!response.ok) {
      return Promise.reject(response);
    }

    if (response.status === 204) {
      return null;
    }

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
      const text = await response.text();
      return text.trim() ? text : null;
    }

    const text = await response.text();
    return text.trim() ? JSON.parse(text) : null;
  });
}

/* ── Profile dropdown ────────────────────────────────────────────────────── */
function bindProfileDropdown() {
  const btn = document.getElementById('profileAvatarBtn');
  const dropdown = document.getElementById('profileDropdown');
  if (!btn || !dropdown) return;

  btn.addEventListener('click', e => {
    e.stopPropagation();
    const open = dropdown.style.display !== 'none';
    dropdown.style.display = open ? 'none' : 'block';
  });

  document.addEventListener('click', e => {
    if (!document.getElementById('profileMenuWrap')?.contains(e.target)) {
      dropdown.style.display = 'none';
    }
  });
}

function closeProfileDropdown() {
  document.getElementById('profileDropdown').style.display = 'none';
}

/* ── Color picker (shared for labels & profile) ──────────────────────────── */
const LABEL_PRESET_COLORS = [
  '#f38ba8', '#eba0ac', '#fab387', '#f9e2af', '#a6e3a1',
  '#94e2d5', '#89dceb', '#74c7ec', '#89b4fa', '#b4befe',
  '#cba6f7', '#f5c2e7', '#f2cdcd', '#cdd6f4', '#bac2de',
  '#a6adc8', '#6c7086', '#585b70', '#45475a', '#313244'
];
const CUSTOM_COLORS_KEY = 'kando_custom_colors';

function loadCustomColors() {
  try { return JSON.parse(localStorage.getItem(CUSTOM_COLORS_KEY) || '[]'); }
  catch (_) { return []; }
}
function saveCustomColorsStore(colors) {
  localStorage.setItem(CUSTOM_COLORS_KEY, JSON.stringify(colors));
}

let activeBoardPickerBtn = null;

function closeBoardColorPickers() {
  document.querySelectorAll('.color-picker-panel').forEach(p => p.remove());
  activeBoardPickerBtn = null;
}

function openBoardColorPicker(btn, onSelect) {
  if (activeBoardPickerBtn === btn) { closeBoardColorPickers(); return; }
  closeBoardColorPickers();
  activeBoardPickerBtn = btn;

  const panel = document.createElement('div');
  panel.className = 'color-picker-panel';
  const swatches = document.createElement('div');
  swatches.className = 'color-swatches';

  LABEL_PRESET_COLORS.forEach(color => {
    const s = document.createElement('button');
    s.type = 'button'; s.className = 'color-swatch';
    s.style.background = color; s.title = color;
    s.addEventListener('click', () => { onSelect(color); closeBoardColorPickers(); });
    swatches.appendChild(s);
  });

  loadCustomColors().forEach(color => {
    const w = document.createElement('div');
    w.className = 'color-swatch-custom';
    const s = document.createElement('button');
    s.type = 'button'; s.className = 'color-swatch';
    s.style.background = color; s.title = color;
    s.addEventListener('click', () => { onSelect(color); closeBoardColorPickers(); });
    const d = document.createElement('button');
    d.type = 'button'; d.className = 'color-swatch-del'; d.title = 'Quitar'; d.textContent = '×';
    d.addEventListener('click', e => {
      e.stopPropagation();
      saveCustomColorsStore(loadCustomColors().filter(c => c !== color));
      w.remove();
    });
    w.append(s, d); swatches.appendChild(w);
  });

  const addBtn = document.createElement('button');
  addBtn.type = 'button'; addBtn.className = 'color-swatch color-swatch-add'; addBtn.textContent = '+';
  const ci = document.createElement('input');
  ci.type = 'color';
  ci.style.cssText = 'position:absolute;width:0;height:0;opacity:0;pointer-events:none';
  ci.addEventListener('change', () => {
    const color = ci.value;
    const customs = loadCustomColors();
    if (!customs.includes(color)) { customs.push(color); saveCustomColorsStore(customs); }
    onSelect(color); closeBoardColorPickers();
  });
  addBtn.addEventListener('click', () => ci.click());
  swatches.appendChild(addBtn);
  panel.appendChild(swatches); panel.appendChild(ci);
  document.body.appendChild(panel);

  const rect = btn.getBoundingClientRect();
  panel.style.top = (rect.bottom + window.scrollY + 6) + 'px';
  panel.style.left = Math.min(rect.left + window.scrollX, window.innerWidth - panel.offsetWidth - 12) + 'px';

  setTimeout(() => {
    document.addEventListener('click', function h(e) {
      if (!panel.contains(e.target) && e.target !== btn) { closeBoardColorPickers(); document.removeEventListener('click', h); }
    });
  }, 0);
}

/* ── Labels modal ────────────────────────────────────────────────────────── */
function openLabelsModal() {
  renderLabelsModal();
  document.getElementById('labelsModal').style.display = 'flex';
}

function closeLabelsModal() {
  closeBoardColorPickers();
  refreshBoardLabelUi();
  document.getElementById('labelsModal').style.display = 'none';
}

function closeLabelsModalOutside(event) {
  if (event.target === document.getElementById('labelsModal')) closeLabelsModal();
}

function renderLabelsModal() {
  const grid = document.getElementById('labelsModalGrid');
  grid.innerHTML = '';

  window.KANDO.labels.forEach(lbl => {
    grid.appendChild(buildLabelRow(lbl));
  });

  grid.appendChild(buildNewLabelRow());
}

function buildLabelRow(lbl) {
  const row = document.createElement('div');
  row.className = 'label-row card';
  row.dataset.labelId = lbl.id;

  const colorBtn = document.createElement('button');
  colorBtn.type = 'button'; colorBtn.className = 'label-color-btn';
  colorBtn.style.background = lbl.color; colorBtn.dataset.color = lbl.color;
  colorBtn.title = 'Cambiar color';

  const nameInput = document.createElement('input');
  nameInput.className = 'label-name-input'; nameInput.value = lbl.name;
  nameInput.maxLength = 64; nameInput.dataset.saved = lbl.name;

  const deleteBtn = document.createElement('button');
  deleteBtn.type = 'button'; deleteBtn.className = 'label-delete-btn';
  deleteBtn.title = 'Eliminar etiqueta';
  deleteBtn.innerHTML = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1.5 14a2 2 0 0 1-2 1.5H8.5a2 2 0 0 1-2-1.5L5 6"/><path d="M10 11v5M14 11v5"/></svg>';

  colorBtn.addEventListener('click', () => {
    openBoardColorPicker(colorBtn, color => {
      colorBtn.style.background = color; colorBtn.dataset.color = color;
      saveLabelInline(lbl.id, nameInput.value.trim() || nameInput.dataset.saved, color);
      const found = window.KANDO.labels.find(l => l.id === lbl.id);
      if (found) found.color = color;
      refreshBoardLabelUi();
    });
  });

  nameInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); nameInput.blur(); }
    if (e.key === 'Escape') { nameInput.value = nameInput.dataset.saved; nameInput.blur(); }
  });
  nameInput.addEventListener('blur', () => {
    const name = nameInput.value.trim();
    if (!name) { nameInput.value = nameInput.dataset.saved; return; }
    if (name === nameInput.dataset.saved) return;
    saveLabelInline(lbl.id, name, colorBtn.dataset.color);
    nameInput.dataset.saved = name;
    const found = window.KANDO.labels.find(l => l.id === lbl.id);
    if (found) found.name = name;
    refreshBoardLabelUi();
  });

  deleteBtn.addEventListener('click', () => {
    if (!confirm('¿Eliminar esta etiqueta? Se quitará de todas las tareas.')) return;
    api('DELETE', `/api/labels/${lbl.id}`)
      .then(() => {
        window.KANDO.labels = window.KANDO.labels.filter(l => l.id !== lbl.id);
        row.remove();
        refreshBoardLabelUi();
      })
      .catch(() => alert('Error al eliminar la etiqueta'));
  });

  row.append(colorBtn, nameInput, deleteBtn);
  return row;
}

function buildNewLabelRow() {
  const row = document.createElement('div');
  row.className = 'label-row label-row-new card';

  const colorBtn = document.createElement('button');
  colorBtn.type = 'button'; colorBtn.className = 'label-color-btn';
  colorBtn.style.background = DEFAULT_LABEL_COLOR;
  colorBtn.dataset.color = DEFAULT_LABEL_COLOR;
  colorBtn.title = 'Elegir color';

  const nameInput = document.createElement('input');
  nameInput.className = 'label-name-input'; nameInput.placeholder = 'Nueva etiqueta…'; nameInput.maxLength = 64;

  colorBtn.addEventListener('click', () => {
    openBoardColorPicker(colorBtn, color => {
      colorBtn.style.background = color; colorBtn.dataset.color = color;
    });
  });

  nameInput.addEventListener('keydown', e => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const name = nameInput.value.trim();
    if (!name) { nameInput.focus(); return; }
    api('POST', '/api/labels', { name, color: colorBtn.dataset.color })
      .then(newLabel => {
        window.KANDO.labels.push(newLabel);
        row.before(buildLabelRow(newLabel));
        nameInput.value = '';
        colorBtn.style.background = DEFAULT_LABEL_COLOR;
        colorBtn.dataset.color = DEFAULT_LABEL_COLOR;
        refreshBoardLabelUi();
        nameInput.focus();
      })
      .catch(async err => alert(await extractApiErrorMessage(err, 'Error al crear la etiqueta')));
  });

  row.append(colorBtn, nameInput);
  return row;
}

function saveLabelInline(id, name, color) {
  api('PUT', `/api/labels/${id}`, { name, color })
    .catch(async err => alert(await extractApiErrorMessage(err, 'Error al guardar la etiqueta')));
}

function refreshBoardLabelUi() {
  const labelIds = new Set((window.KANDO.labels || []).map(label => String(label.id)));
  document.querySelectorAll('.task-card[data-task-id]').forEach(card => {
    if (card.dataset.labelId && !labelIds.has(card.dataset.labelId)) {
      delete card.dataset.labelId;
    }
    syncTaskCardPresentation(card);
  });

  if (currentLabelId && !labelIds.has(String(currentLabelId))) {
    currentLabelId = null;
  }

  if (document.getElementById('taskModal')?.style.display !== 'none') {
    renderLabelPicker();
    renderParentPicker();
    renderOpenTaskSubtasks();
  }

  syncBoardLabelFilterText();
  renderBoardLabelFilterOptions();
  applyBoardFilters();
}

/* ── Profile modal ───────────────────────────────────────────────────────── */
let profileAvatarColor = '#cba6f7';

function openProfileModal() {
  const u = window.KANDO.user;
  profileAvatarColor = u.avatarColor || '#cba6f7';

  document.getElementById('profileDisplayName').value = u.displayName || '';
  document.getElementById('profileUsername').value = u.username || '';
  document.getElementById('profileEmail').value = u.email || '';
  document.getElementById('profileCurrentPwd').value = '';
  document.getElementById('profileNewPwd').value = '';
  document.getElementById('profileConfirmPwd').value = '';
  document.getElementById('profileUsernameHelp').textContent = '';
  document.getElementById('profileSaveMsg').textContent = '';
  document.getElementById('pwdStrength').style.display = 'none';

  updateProfileAvatarPreview();
  bindProfileModalEvents();
  document.getElementById('profileModal').style.display = 'flex';
  setTimeout(() => document.getElementById('profileDisplayName').focus(), 50);
}

function closeProfileModal() {
  closeBoardColorPickers();
  document.getElementById('profileModal').style.display = 'none';
}

function closeProfileModalOutside(event) {
  if (event.target === document.getElementById('profileModal')) closeProfileModal();
}

function updateProfileAvatarPreview() {
  const preview = document.getElementById('profileAvatarPreview');
  const initials = document.getElementById('profileAvatarInitials');
  if (!preview || !initials) return;
  preview.style.background = profileAvatarColor;
  const nameVal = document.getElementById('profileDisplayName')?.value.trim()
    || document.getElementById('profileUsername')?.value.trim() || '?';
  initials.textContent = nameVal.charAt(0).toUpperCase();
}

let profileModalBound = false;
function bindProfileModalEvents() {
  if (profileModalBound) return;
  profileModalBound = true;

  document.getElementById('profileAvatarPreview').addEventListener('click', () => {
    openBoardColorPicker(document.getElementById('profileAvatarPreview'), color => {
      profileAvatarColor = color;
      updateProfileAvatarPreview();
    });
  });

  document.getElementById('profileDisplayName').addEventListener('input', updateProfileAvatarPreview);

  document.getElementById('profileNewPwd').addEventListener('input', () => {
    const pwd = document.getElementById('profileNewPwd').value;
    const block = document.getElementById('pwdStrength');
    block.style.display = pwd ? 'flex' : 'none';
    setPwdReq('pwdLen',   pwd.length >= 8);
    setPwdReq('pwdUpper', /[A-Z]/.test(pwd));
    setPwdReq('pwdLower', /[a-z]/.test(pwd));
    setPwdReq('pwdDigit', /[0-9]/.test(pwd));
  });

  let usernameCheckTimer = null;
  document.getElementById('profileUsername').addEventListener('input', () => {
    clearTimeout(usernameCheckTimer);
    const val = document.getElementById('profileUsername').value.trim();
    const help = document.getElementById('profileUsernameHelp');
    if (!val || val === window.KANDO.user.username) { help.textContent = ''; help.className = 'field-help'; return; }
    usernameCheckTimer = setTimeout(() => {
      fetch(`/api/profile/check-username?username=${encodeURIComponent(val)}`)
        .then(r => r.json())
        .then(({ available }) => {
          help.textContent = available ? '✓ Disponible' : '✗ Ya está en uso';
          help.className = available ? 'field-help field-help-ok' : 'field-help field-help-error';
        })
        .catch(() => {});
    }, 400);
  });
}

function setPwdReq(id, ok) {
  const el = document.getElementById(id);
  if (!el) return;
  el.classList.toggle('pwd-req-ok', ok);
  el.classList.toggle('pwd-req-fail', !ok);
}

function saveProfile() {
  const displayName = document.getElementById('profileDisplayName').value.trim();
  const username    = document.getElementById('profileUsername').value.trim();
  const email       = document.getElementById('profileEmail').value.trim();
  const currentPwd  = document.getElementById('profileCurrentPwd').value;
  const newPwd      = document.getElementById('profileNewPwd').value;
  const confirmPwd  = document.getElementById('profileConfirmPwd').value;
  const msgEl       = document.getElementById('profileSaveMsg');

  if (!username) { msgEl.textContent = 'El login no puede estar vacío.'; msgEl.className = 'profile-save-msg error'; return; }

  if (newPwd) {
    if (!currentPwd) { msgEl.textContent = 'Introduce la contraseña actual para cambiarla.'; msgEl.className = 'profile-save-msg error'; return; }
    if (newPwd !== confirmPwd) { msgEl.textContent = 'Las contraseñas nuevas no coinciden.'; msgEl.className = 'profile-save-msg error'; return; }
    if (newPwd.length < 8 || !/[A-Z]/.test(newPwd) || !/[a-z]/.test(newPwd) || !/[0-9]/.test(newPwd)) {
      msgEl.textContent = 'La contraseña no cumple los requisitos de seguridad.'; msgEl.className = 'profile-save-msg error'; return;
    }
  }

  const payload = { displayName, username, email, avatarColor: profileAvatarColor };
  if (newPwd) { payload.currentPassword = currentPwd; payload.newPassword = newPwd; }

  api('PUT', '/api/profile', payload)
    .then(data => {
      window.KANDO.user = {
        username: data.username, displayName: data.displayName,
        email: data.email, avatarColor: data.avatarColor, initials: data.initials
      };
      updateNavbarAvatar(data);
      if (data.usernameChanged) {
        closeProfileModal();
        alert('Login actualizado. Vuelve a iniciar sesión.');
        window.location.href = '/login';
      } else {
        msgEl.textContent = 'Guardado.'; msgEl.className = 'profile-save-msg ok';
        setTimeout(() => closeProfileModal(), 900);
      }
    })
    .catch(async err => {
      msgEl.textContent = await extractApiErrorMessage(err, 'Error al guardar el perfil.');
      msgEl.className = 'profile-save-msg error';
    });
}

function updateNavbarAvatar(data) {
  const btn = document.getElementById('profileAvatarBtn');
  if (!btn) return;
  btn.style.background = data.avatarColor;
  const span = btn.querySelector('span');
  if (span) span.textContent = data.initials;
  const nameEl = document.querySelector('.profile-dropdown-header strong');
  if (nameEl) nameEl.textContent = data.displayName || data.username;
  const emailEl = document.querySelector('.profile-email');
  if (emailEl) emailEl.textContent = data.email || '';
}

function openVersionModal() {
  document.getElementById('versionModal').style.display = 'flex';
}
function closeVersionModal() {
  document.getElementById('versionModal').style.display = 'none';
}
function closeVersionModalOutside(event) {
  if (event.target === document.getElementById('versionModal')) closeVersionModal();
}
