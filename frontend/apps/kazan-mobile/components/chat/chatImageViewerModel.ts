/*
 * The gestures of the full-screen image viewer: how far a zoomed image may
 * move, and when a swipe down at fit size closes the viewer. The functions
 * run on the UI thread inside the gestures (hence the worklet directive) and
 * in the unit tests alike.
 */

/** A swipe down past this many points closes the viewer when released. */
export const CHAT_VIEWER_DISMISS_DISTANCE = 120;

/** A quicker swipe down closes the viewer at any distance. */
export const CHAT_VIEWER_DISMISS_VELOCITY = 800;

/** A width and a height in points, or the pixel size of an image. */
export interface ChatViewerSize {
  width: number;
  height: number;
}

/**
 * The size the image is drawn at inside the box at fit size: scaled to fit
 * whole (`contentFit="contain"`), so a landscape photo in a tall box leaves
 * black bands above and below it. Without a known image size the box itself
 * stands in for it.
 */
export function chatViewerContentSize(
  box: ChatViewerSize,
  image: ChatViewerSize | null
): ChatViewerSize {
  "worklet";
  if (!image || image.width <= 0 || image.height <= 0) return box;
  const fit: number = Math.min(box.width / image.width, box.height / image.height);
  return { width: image.width * fit, height: image.height * fit };
}

/**
 * How far a zoomed image may move along one axis: the drawn image `content`
 * points long, zoomed by `scale`, inside a box `box` points long. It moves
 * only by what overflows the box, so its edge never leaves the box and the
 * black ground never shows beyond it; zero while it still fits.
 */
export function chatViewerPanLimit(box: number, content: number, scale: number): number {
  "worklet";
  if (box <= 0 || scale <= 1) return 0;
  return Math.max(0, (content * scale - box) / 2);
}

/** `value` held within plus and minus `limit`. */
export function chatViewerClamp(value: number, limit: number): number {
  "worklet";
  return Math.min(limit, Math.max(-limit, value));
}

/** True when a swipe down at fit size, released with this travel and speed, closes the viewer. */
export function chatViewerDismisses(translationY: number, velocityY: number): boolean {
  "worklet";
  return (
    translationY > CHAT_VIEWER_DISMISS_DISTANCE ||
    (translationY > 0 && velocityY > CHAT_VIEWER_DISMISS_VELOCITY)
  );
}
