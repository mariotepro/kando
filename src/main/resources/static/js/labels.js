/* ── Preset & custom colors ──────────────────────────────────────────────── */
const PRESET_COLORS = [
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

function saveCustomColors(colors) {
  localStorage.setItem(CUSTOM_COLORS_KEY, JSON.stringify(colors));
}

function addCustomColor(color) {
  const colors = loadCustomColors();
  if (!colors.includes(color)) {
    colors.push(color);
    saveCustomColors(colors);
  }
}

function removeCustomColor(color) {
  saveCustomColors(loadCustomColors().filter(c => c !== color));
}

/* ── Color picker panel ──────────────────────────────────────────────────── */
let activePickerBtn = null;

function closeAllPickers() {
  document.querySelectorAll('.color-picker-panel').forEach(p => p.remove());
  activePickerBtn = null;
}

function openColorPicker(btn, onSelect) {
  if (activePickerBtn === btn) {
    closeAllPickers();
    return;
  }
  closeAllPickers();
  activePickerBtn = btn;

  const panel = document.createElement('div');
  panel.className = 'color-picker-panel';

  const swatches = document.createElement('div');
  swatches.className = 'color-swatches';

  PRESET_COLORS.forEach(color => swatches.appendChild(makePresetSwatch(color, onSelect)));
  loadCustomColors().forEach(color => swatches.appendChild(makeCustomSwatch(color, onSelect)));

  const addBtn = document.createElement('button');
  addBtn.type = 'button';
  addBtn.className = 'color-swatch color-swatch-add';
  addBtn.title = 'Color personalizado';
  addBtn.textContent = '+';

  const customInput = document.createElement('input');
  customInput.type = 'color';
  customInput.style.cssText = 'position:absolute;width:0;height:0;opacity:0;pointer-events:none';
  customInput.addEventListener('change', () => {
    const color = customInput.value;
    addCustomColor(color);
    onSelect(color);
    closeAllPickers();
  });

  addBtn.addEventListener('click', () => customInput.click());
  swatches.appendChild(addBtn);
  panel.appendChild(swatches);
  panel.appendChild(customInput);
  document.body.appendChild(panel);

  const rect = btn.getBoundingClientRect();
  panel.style.top = (rect.bottom + window.scrollY + 6) + 'px';
  panel.style.left = Math.min(
    rect.left + window.scrollX,
    window.innerWidth - panel.offsetWidth - 12
  ) + 'px';

  setTimeout(() => {
    document.addEventListener('click', function outsideHandler(e) {
      if (!panel.contains(e.target) && e.target !== btn) {
        closeAllPickers();
        document.removeEventListener('click', outsideHandler);
      }
    });
  }, 0);
}

function makePresetSwatch(color, onSelect) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'color-swatch';
  btn.style.background = color;
  btn.title = color;
  btn.addEventListener('click', () => { onSelect(color); closeAllPickers(); });
  return btn;
}

function makeCustomSwatch(color, onSelect) {
  const wrapper = document.createElement('div');
  wrapper.className = 'color-swatch-custom';

  const swatch = document.createElement('button');
  swatch.type = 'button';
  swatch.className = 'color-swatch';
  swatch.style.background = color;
  swatch.title = color;
  swatch.addEventListener('click', () => { onSelect(color); closeAllPickers(); });

  const del = document.createElement('button');
  del.type = 'button';
  del.className = 'color-swatch-del';
  del.title = 'Quitar color personalizado';
  del.textContent = '×';
  del.addEventListener('click', e => {
    e.stopPropagation();
    removeCustomColor(color);
    wrapper.remove();
  });

  wrapper.append(swatch, del);
  return wrapper;
}

/* ── Generic confirm/alert modals (reemplazan confirm()/alert()) ────────────── */
let _confirmModalResolve = null;

function showConfirmModal(title, message, confirmLabel = 'Eliminar') {
  document.getElementById('confirmModalTitle').textContent = title;
  document.getElementById('confirmModalCopy').textContent = message;
  const button = document.getElementById('confirmModalButton');
  button.textContent = confirmLabel;
  button.disabled = false;
  document.getElementById('confirmModal').style.display = 'flex';
  setTimeout(() => button.focus(), 50);
  return new Promise(resolve => { _confirmModalResolve = resolve; });
}

function confirmConfirmModal() { closeConfirmModal(true); }
function cancelConfirmModal() { closeConfirmModal(false); }

function closeConfirmModal(result) {
  document.getElementById('confirmModal').style.display = 'none';
  if (_confirmModalResolve) { _confirmModalResolve(result); _confirmModalResolve = null; }
}

function cancelConfirmModalOutside(event) {
  if (event.target === document.getElementById('confirmModal')) cancelConfirmModal();
}

let _alertModalResolve = null;

function showAlertModal(message, title = 'Aviso') {
  document.getElementById('alertModalTitle').textContent = title;
  document.getElementById('alertModalCopy').textContent = message;
  document.getElementById('alertModal').style.display = 'flex';
  setTimeout(() => document.getElementById('alertModalButton').focus(), 50);
  return new Promise(resolve => { _alertModalResolve = resolve; });
}

function closeAlertModal() {
  document.getElementById('alertModal').style.display = 'none';
  if (_alertModalResolve) { _alertModalResolve(); _alertModalResolve = null; }
}

function closeAlertModalOutside(event) {
  if (event.target === document.getElementById('alertModal')) closeAlertModal();
}

document.addEventListener('keydown', e => {
  const confirmModal = document.getElementById('confirmModal');
  if (confirmModal && confirmModal.style.display !== 'none') {
    if (e.key === 'Enter') confirmConfirmModal();
    if (e.key === 'Escape') cancelConfirmModal();
  }
  const alertModal = document.getElementById('alertModal');
  if (alertModal && alertModal.style.display !== 'none' && (e.key === 'Enter' || e.key === 'Escape')) {
    closeAlertModal();
  }
});

/* ── Existing label rows ─────────────────────────────────────────────────── */
function bindLabelRow(row) {
  const labelId = parseInt(row.dataset.labelId, 10);
  const colorBtn = row.querySelector('.label-color-btn');
  const nameInput = row.querySelector('.label-name-input');
  const deleteBtn = row.querySelector('.label-delete-btn');

  nameInput.dataset.saved = nameInput.value;

  colorBtn.addEventListener('click', () => {
    openColorPicker(colorBtn, color => {
      colorBtn.style.background = color;
      colorBtn.dataset.color = color;
      saveLabel(labelId, nameInput.value.trim() || nameInput.dataset.saved, color);
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
    saveLabel(labelId, name, colorBtn.dataset.color);
    nameInput.dataset.saved = name;
  });

  deleteBtn.addEventListener('click', async () => {
    const confirmed = await showConfirmModal('Eliminar etiqueta', '¿Eliminar esta etiqueta? Se quitará de todas las tareas que la usen.');
    if (!confirmed) return;
    fetch(`/api/labels/${labelId}`, { method: 'DELETE' })
      .then(r => r.ok ? location.reload() : showAlertModal('Error al eliminar'));
  });
}

function saveLabel(id, name, color) {
  if (!name) return;
  fetch(`/api/labels/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, color })
  }).then(r => { if (!r.ok) r.json().then(e => showAlertModal(e.message || 'Error al guardar')); });
}

/* ── New label row ───────────────────────────────────────────────────────── */
function bindNewLabelRow() {
  const colorBtn = document.getElementById('newLabelColorBtn');
  const nameInput = document.getElementById('newLabelName');
  if (!colorBtn || !nameInput) return;

  colorBtn.addEventListener('click', () => {
    openColorPicker(colorBtn, color => {
      colorBtn.style.background = color;
      colorBtn.dataset.color = color;
    });
  });

  nameInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); createLabel(); }
  });
}

function createLabel() {
  const colorBtn = document.getElementById('newLabelColorBtn');
  const nameInput = document.getElementById('newLabelName');
  const name = nameInput.value.trim();
  const color = colorBtn?.dataset.color || '#cba6f7';
  if (!name) { nameInput.focus(); return; }

  fetch('/api/labels', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, color, boardId: window.KANDO.activeBoardId })
  }).then(r => r.ok ? location.reload() : r.json().then(e => showAlertModal(e.message || 'Error')));
}

/* ── Init ────────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.label-row[data-label-id]').forEach(bindLabelRow);
  bindNewLabelRow();
});
