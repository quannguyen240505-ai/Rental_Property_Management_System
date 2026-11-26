const el = id => document.getElementById(id);
async function request(path, method, body) {
  if (!el('token').value.trim()) { el('message').textContent = 'Vui lòng nhập access token.'; return; }
  document.querySelectorAll('button').forEach(b => b.disabled = true);
  el('message').textContent = 'Đang xử lý…';
  try {
    const response = await fetch('/api/deposit-receipts' + path, {
      method, headers: {Authorization: 'Bearer ' + el('token').value.trim(), 'Content-Type': 'application/json'},
      ...(body ? {body: JSON.stringify(body)} : {})
    });
    const data = await response.json();
    el('result').textContent = JSON.stringify(data, null, 2);
    el('message').textContent = response.ok ? 'Thành công · HTTP ' + response.status : 'Yêu cầu chưa thành công · HTTP ' + response.status;
    if (response.ok && data.id) el('receipt').value = data.id;
  } catch (error) { el('message').textContent = 'Không thể kết nối Payment. Kiểm tra service rồi thử lại.'; }
  finally { document.querySelectorAll('button').forEach(b => b.disabled = false); }
}
el('create').onclick = () => {
  const amount = Number(el('amount').value);
  if (!el('hold').value.trim() || !Number.isSafeInteger(amount) || amount <= 0) {
    el('message').textContent = 'Nhập Hold ID và số tiền nguyên lớn hơn 0.'; return;
  }
  request('', 'POST', {holdId: el('hold').value.trim(), amount, paymentMethod: el('method').value, evidenceUrl: el('evidence').value.trim() || null});
};
const receiptPath = suffix => {
  if (!el('receipt').value.trim()) { el('message').textContent = 'Vui lòng nhập Receipt ID.'; return; }
  request('/' + encodeURIComponent(el('receipt').value.trim()) + suffix, suffix === '/verify' ? 'POST' : 'GET');
};
el('verify').onclick = () => { if (confirm('Bạn đã kiểm tra và thực sự nhận đủ số tiền trên phiếu?')) receiptPath('/verify'); };
el('check').onclick = () => receiptPath('/rental-eligibility');
