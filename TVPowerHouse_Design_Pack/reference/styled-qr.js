/* Reference implementation of the "TV Power House" styled QR code.
 * Input : matrix = array of strings ("1"/"0"), size N x N (from nayuki QrCode, ECC = HIGH)
 * Output: SVG string. The Android renderer (android/java/StyledQrRenderer.java)
 *         must reproduce exactly this geometry. All numbers are in "module units".
 */
function styledQrSvg(matrix, o) {
  o = Object.assign({
    ink: '#14110F',          // dark modules
    paper: '#FAF8F4',        // card / light modules (warm white, NOT pure grey)
    eye: '#E0501A',          // finder-pattern centre (deep orange, still high contrast)
    quiet: 4,                // quiet zone in modules (minimum for reliable scanning)
    logo: null,              // data-URI or url of the logo, null = no centre logo
    logoModules: 7,          // centre plate width in modules (max 19% of width)
    id: 'q'
  }, o || {});
  const N = matrix.length, Q = o.quiet, S = N + Q * 2;
  const on = (x, y) => x >= 0 && y >= 0 && x < N && y < N && matrix[y][x] === '1';
  const inFinder = (x, y) => (x < 8 && y < 8) || (x >= N - 8 && y < 8) || (x < 8 && y >= N - 8);
  const mid = N / 2, half = o.logoModules / 2;
  const inLogo = (x, y) => Math.abs(x + .5 - mid) < half + .5 && Math.abs(y + .5 - mid) < half + .5;
  const R = 0.5; // corner radius of an isolated module (0.5 = full circle-ish)
  let d = '';
  const rr = (x, y, tl, tr, br, bl) => { // one module with per-corner radii
    const r = (v) => v ? R : 0;
    return `M${x + r(tl)} ${y}H${x + 1 - r(tr)}` + (tr ? `A${R} ${R} 0 0 1 ${x + 1} ${y + R}` : '') +
      `V${y + 1 - r(br)}` + (br ? `A${R} ${R} 0 0 1 ${x + 1 - R} ${y + 1}` : '') +
      `H${x + r(bl)}` + (bl ? `A${R} ${R} 0 0 1 ${x} ${y + 1 - R}` : '') +
      `V${y + r(tl)}` + (tl ? `A${R} ${R} 0 0 1 ${x + R} ${y}` : '') + 'Z';
  };
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (!on(x, y) || inFinder(x, y) || inLogo(x, y)) continue;
    const U = on(x, y - 1), D = on(x, y + 1), L = on(x - 1, y), Rr = on(x + 1, y);
    // round a corner only when BOTH neighbours touching that corner are empty -> liquid, connected look
    d += rr(x + Q, y + Q, !U && !L, !U && !Rr, !D && !Rr, !D && !L);
  }
  const eyes = [[0, 0], [N - 7, 0], [0, N - 7]].map(([ex, ey]) => {
    const x = ex + Q, y = ey + Q;
    return `<rect x="${x + .5}" y="${y + .5}" width="6" height="6" rx="1.9" fill="none" stroke="${o.ink}" stroke-width="1"/>` +
           `<rect x="${x + 2}" y="${y + 2}" width="3" height="3" rx="1" fill="${o.eye}"/>`;
  }).join('');
  const lp = o.logoModules, lx = Q + mid - lp / 2;
  const logo = o.logo ? `
    <rect x="${lx}" y="${lx}" width="${lp}" height="${lp}" rx="1.8" fill="${o.paper}"/>
    <clipPath id="${o.id}c"><rect x="${lx + .45}" y="${lx + .45}" width="${lp - .9}" height="${lp - .9}" rx="1.45"/></clipPath>
    <image href="${o.logo}" x="${lx + .45}" y="${lx + .45}" width="${lp - .9}" height="${lp - .9}" clip-path="url(#${o.id}c)" preserveAspectRatio="xMidYMid slice"/>` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${S} ${S}" shape-rendering="geometricPrecision">
    <rect width="${S}" height="${S}" fill="${o.paper}"/>
    <path d="${d}" fill="${o.ink}"/>${eyes}${logo}</svg>`;
}
if (typeof module !== 'undefined') module.exports = { styledQrSvg };
