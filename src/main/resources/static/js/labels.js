function showCreateForm() {
  document.getElementById('createForm').style.display = 'block';
  document.getElementById('newLabelName').focus();
}

function hideCreateForm() {
  document.getElementById('createForm').style.display = 'none';
  document.getElementById('newLabelName').value = '';
}

function createLabel() {
  const name = document.getElementById('newLabelName').value.trim();
  const color = document.getElementById('newLabelColor').value;
  if (!name) { document.getElementById('newLabelName').focus(); return; }

  fetch('/api/labels', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, color })
  }).then(r => r.ok ? location.reload() : r.json().then(e => alert(e.message || 'Error')));
}

function toggleEditLabel(id, btn) {
  const row = document.querySelector(`.label-row[data-label-id="${id}"]`);
  const preview = row.querySelector('.label-preview');
  const editRow = row.querySelector('.label-edit-row');
  const isEditing = editRow.style.display === 'flex';
  preview.style.display  = isEditing ? 'flex' : 'none';
  editRow.style.display  = isEditing ? 'none' : 'flex';
  btn.textContent = isEditing ? 'Editar' : 'Cancelar';
}

function saveLabel(id, btn) {
  const row = document.querySelector(`.label-row[data-label-id="${id}"]`);
  const name  = row.querySelector('.edit-name').value.trim();
  const color = row.querySelector('.edit-color').value;
  if (!name) return;

  fetch(`/api/labels/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, color })
  }).then(r => r.ok ? location.reload() : r.json().then(e => alert(e.message || 'Error')));
}

function deleteLabel(id, btn) {
  if (!confirm('¿Eliminar esta etiqueta? Se quitará de todas las tareas que la usen.')) return;
  fetch(`/api/labels/${id}`, { method: 'DELETE' })
    .then(r => r.ok ? location.reload() : alert('Error al eliminar'));
}
