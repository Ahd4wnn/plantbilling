/**
 * Turning a shop logo into something a thermal printer can print.
 *
 * The receipt compiler in `store/bluetooth.ts` is text-only — it strips every
 * non-ASCII byte and emits nothing but ESC/POS text commands. An image has to go
 * out as a raster bitmap instead, via `GS v 0`.
 *
 * A thermal head has exactly two states per dot: burn or don't. There is no grey.
 * So the logo is scaled to the printer's dot width, converted to luminance, and
 * then dithered to 1-bit with Floyd–Steinberg — a flat threshold turns anything
 * with a gradient or an anti-aliased edge into a harsh blob, whereas diffusing the
 * error across neighbouring dots preserves the shape at a glance, which is all a
 * 58 mm receipt can convey anyway.
 *
 * Nothing here throws for a bad logo: the caller prints the receipt without it.
 * A missing logo is a cosmetic problem; a receipt that won't print is not.
 */

/** Dots across the head. 58 mm paper is 384; 80 mm is 576. */
export const PRINTER_DOTS_58MM = 384;
export const PRINTER_DOTS_80MM = 576;

/** Cap the logo's height so it can't push the whole bill off the paper. */
const MAX_LOGO_DOTS_TALL = 200;

/** Below this luminance (0–255) a dot is burned black. */
const THRESHOLD = 128;

/**
 * Load an image URL into a canvas and return its pixels.
 *
 * `crossOrigin` is set because the logo is served from the API host while the app
 * may be on another origin; without it the canvas is tainted and getImageData
 * throws a SecurityError.
 */
async function loadPixels(
  url: string,
  maxWidth: number,
): Promise<{ data: Uint8ClampedArray; width: number; height: number } | null> {
  if (typeof document === "undefined") return null;

  const img = await new Promise<HTMLImageElement | null>((resolve) => {
    const el = new Image();
    el.crossOrigin = "anonymous";
    el.onload = () => resolve(el);
    el.onerror = () => resolve(null);
    el.src = url;
  });
  if (!img || !img.width || !img.height) return null;

  // Scale to fit the head, preserving aspect ratio, and never upscale — blowing a
  // small logo up to 384 dots just prints a bigger blur.
  const scale = Math.min(maxWidth / img.width, MAX_LOGO_DOTS_TALL / img.height, 1);
  // Width must land on a byte boundary: GS v 0 sends whole bytes per row.
  const width = Math.max(8, Math.floor((img.width * scale) / 8) * 8);
  const height = Math.max(1, Math.round(img.height * scale));

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  if (!ctx) return null;

  // White backdrop first: a transparent PNG logo would otherwise read as black
  // pixels and print as a solid rectangle.
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, width, height);
  ctx.drawImage(img, 0, 0, width, height);

  try {
    const { data } = ctx.getImageData(0, 0, width, height);
    return { data, width, height };
  } catch {
    return null; // tainted canvas — no CORS headers on the media host
  }
}

/**
 * Composite onto white, convert to luminance, and dither to 1 bit in place.
 * Returns one byte per pixel: 0 = burn, 255 = leave blank.
 */
function ditherToMono(rgba: Uint8ClampedArray, width: number, height: number): Float32Array {
  const grey = new Float32Array(width * height);
  for (let i = 0; i < width * height; i++) {
    const r = rgba[i * 4];
    const g = rgba[i * 4 + 1];
    const b = rgba[i * 4 + 2];
    const a = rgba[i * 4 + 3] / 255;
    // Rec. 601 luma, then flattened onto white by alpha.
    const luma = 0.299 * r + 0.587 * g + 0.114 * b;
    grey[i] = luma * a + 255 * (1 - a);
  }

  // Floyd–Steinberg: push each pixel's rounding error onto the neighbours that
  // haven't been decided yet, so large flat areas keep their average darkness.
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = y * width + x;
      const old = grey[i];
      const next = old < THRESHOLD ? 0 : 255;
      grey[i] = next;
      const err = old - next;
      if (x + 1 < width) grey[i + 1] += (err * 7) / 16;
      if (y + 1 < height) {
        if (x > 0) grey[i + width - 1] += (err * 3) / 16;
        grey[i + width] += (err * 5) / 16;
        if (x + 1 < width) grey[i + width + 1] += (err * 1) / 16;
      }
    }
  }
  return grey;
}

/**
 * Encode a logo URL as an ESC/POS raster command, centred, ready to prepend to a
 * receipt. Returns null whenever the logo can't be used — the caller prints
 * without it rather than failing.
 */
export async function encodeLogoRaster(
  url: string,
  dotsWide: number = PRINTER_DOTS_58MM,
): Promise<Uint8Array | null> {
  try {
    const pixels = await loadPixels(url, dotsWide);
    if (!pixels) return null;
    const { data, width, height } = pixels;

    const mono = ditherToMono(data, width, height);
    const bytesPerRow = width / 8; // width is a multiple of 8 by construction
    const raster = new Uint8Array(bytesPerRow * height);

    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        // A set bit burns a dot, so black (0) becomes 1.
        if (mono[y * width + x] < THRESHOLD) {
          raster[y * bytesPerRow + (x >> 3)] |= 0x80 >> (x & 7);
        }
      }
    }

    // ESC a 1 (centre) · GS v 0 m xL xH yL yH <data> · ESC a 0 (back to left)
    const header = [
      0x1b, 0x61, 0x01,
      0x1d, 0x76, 0x30, 0x00,
      bytesPerRow & 0xff, (bytesPerRow >> 8) & 0xff,
      height & 0xff, (height >> 8) & 0xff,
    ];
    const out = new Uint8Array(header.length + raster.length + 4);
    out.set(header, 0);
    out.set(raster, header.length);
    // A blank line under the logo, then re-align left for the text that follows.
    out.set([0x0a, 0x1b, 0x61, 0x00], header.length + raster.length);
    return out;
  } catch {
    return null;
  }
}
