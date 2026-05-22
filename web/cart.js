

// ── Mobile nav toggle (UI only) ──
function toggleMenu() {
  var nav = document.querySelector('.nav-right');
  if (nav) nav.classList.toggle('open');
}

function updateCartBadge() {
  fetch('/cart/count')
    .then(function(res) {
      console.log("Status:", res.status);
      return res.text();
    })
    .then(function(count) {
      console.log("Count from server:", count);

      var badges = document.querySelectorAll('#cart-count');
      badges.forEach(function(el) { el.textContent = count; });
    })
    .catch(function(err) {
      console.log("Fetch error:", err);

      var badges = document.querySelectorAll('#cart-count');
      badges.forEach(function(el) { el.textContent = '0'; });
    });
}

// ── Run on every page load ──
document.addEventListener('DOMContentLoaded', function() {
  updateCartBadge();
});
