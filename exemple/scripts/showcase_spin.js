({
  _t: 0,

  _process: function (api, dt) {
    // Simple script smoke-test: slowly rotate the CharacterBody node itself.
    this._t += dt;
    if (this._t > 1000000) this._t = 0;
    if (!api) return;
    // Keep it gentle so it doesn't fight player input too hard.
    // (This also proves script hot-reload + per-tick updates work.)
    var yaw = Math.sin(this._t) * 5.0;
    api.set("ry", String(yaw));
  }
})
