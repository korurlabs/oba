/** Horizontal run out of the dependency and into the dependent before any turn. */
export const CONNECTOR_GAP_PX = 12;
/** Corner radius; a corner between short segments gets a smaller one. */
export const CONNECTOR_RADIUS_PX = 6;
/** Arrowhead length along the line and width across it. */
export const CONNECTOR_ARROW_LENGTH_PX = 6;
export const CONNECTOR_ARROW_WIDTH_PX = 7;

export type ConnectorInput = {
  /** Right edge of the dependency's bar, or the right tip of its diamond. */
  fromX: number;
  /** Vertical centre of the dependency's row. */
  fromY: number;
  /** Left edge of the dependent's bar, or the left tip of its diamond. */
  toX: number;
  /** Vertical centre of the dependent's row. */
  toY: number;
  /** Height of a milestone row; its edges are the gutters a backward connector runs along. */
  rowHeight: number;
  gap?: number;
  radius?: number;
  arrowLength?: number;
  arrowWidth?: number;
};

export type ConnectorGeometry = {
  /** The stroked line, ending where the arrowhead starts. */
  path: string;
  /** A closed triangle whose tip touches the dependent's marker. */
  arrow: string;
  /**
   * `direct` when there is room for a turn between the two markers;
   * `gutter` when the dependent starts too early and the line runs back
   * along the row edge instead of through either marker.
   */
  route: "direct" | "gutter";
};

type Point = { x: number; y: number };

/**
 * An orthogonal connector from a dependency to its dependent. It always
 * leaves the dependency to the right and enters the dependent from the left,
 * so the direction reads the same whatever the dates are.
 *
 * With room between the markers it turns once just after the dependency and
 * runs along the dependent's row into it. Without room it drops to the edge
 * of the dependent's row nearest the dependency, runs back along that edge
 * to just before the dependent, and enters it from the left; the line then
 * never crosses or runs along a marker. Corners are rounded.
 */
export function buildConnectorPath(input: ConnectorInput): ConnectorGeometry {
  const {
    fromX,
    fromY,
    toX,
    toY,
    rowHeight,
    gap = CONNECTOR_GAP_PX,
    radius = CONNECTOR_RADIUS_PX,
    arrowLength = CONNECTOR_ARROW_LENGTH_PX,
    arrowWidth = CONNECTOR_ARROW_WIDTH_PX,
  } = input;

  const end: Point = { x: toX - arrowLength, y: toY };
  const outX = fromX + gap;
  const direct = toX >= fromX + 2 * gap;

  let points: Point[];
  if (direct) {
    points =
      fromY === toY
        ? [{ x: fromX, y: fromY }, end]
        : [{ x: fromX, y: fromY }, { x: outX, y: fromY }, { x: outX, y: toY }, end];
  } else {
    // The dependent's row edge facing the dependency; for a dependent on the
    // same row, the edge below it.
    const gutterY = toY > fromY ? toY - rowHeight / 2 : toY + rowHeight / 2;
    const inX = toX - gap;
    points = [
      { x: fromX, y: fromY },
      { x: outX, y: fromY },
      { x: outX, y: gutterY },
      { x: inX, y: gutterY },
      { x: inX, y: toY },
      end,
    ];
  }

  const half = arrowWidth / 2;
  const arrow = `M ${fmt(toX)} ${fmt(toY)} L ${fmt(end.x)} ${fmt(toY - half)} L ${fmt(end.x)} ${fmt(toY + half)} Z`;

  return {
    path: roundedPolyline(simplify(points), radius),
    arrow,
    route: direct ? "direct" : "gutter",
  };
}

/** Drops repeated points and the middle one of three collinear points, which would leave zero-size corners. */
function simplify(points: Point[]): Point[] {
  const unique: Point[] = [];
  for (const point of points) {
    const last = unique[unique.length - 1];
    if (!last || last.x !== point.x || last.y !== point.y) unique.push(point);
  }
  const result: Point[] = [];
  for (const point of unique) {
    const b = result[result.length - 1];
    const a = result[result.length - 2];
    if (a && b && ((a.x === b.x && b.x === point.x) || (a.y === b.y && b.y === point.y))) {
      result[result.length - 1] = point;
    } else {
      result.push(point);
    }
  }
  return result;
}

/**
 * An SVG path through axis-aligned points with each corner rounded. A corner
 * takes at most half of a segment it shares with another corner, and all of a
 * segment that ends the line, so neighbouring curves never overlap.
 */
function roundedPolyline(points: Point[], radius: number): string {
  const first = points[0];
  if (!first) return "";
  const last = points.length - 1;
  let d = `M ${fmt(first.x)} ${fmt(first.y)}`;
  let current: Point = first;
  // A straight piece that a corner used up entirely is left out.
  const lineTo = (point: Point) => {
    if (point.x !== current.x || point.y !== current.y) d += ` L ${fmt(point.x)} ${fmt(point.y)}`;
  };
  for (let i = 1; i < last; i++) {
    const previous = points[i - 1]!;
    const corner = points[i]!;
    const next = points[i + 1]!;
    const lengthIn = distance(previous, corner);
    const lengthOut = distance(corner, next);
    const r = Math.min(
      radius,
      i - 1 === 0 ? lengthIn : lengthIn / 2,
      i + 1 === last ? lengthOut : lengthOut / 2,
    );
    const before = towards(corner, previous, r);
    const after = towards(corner, next, r);
    lineTo(before);
    d += ` Q ${fmt(corner.x)} ${fmt(corner.y)} ${fmt(after.x)} ${fmt(after.y)}`;
    current = after;
  }
  lineTo(points[last]!);
  return d;
}

function distance(a: Point, b: Point): number {
  return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
}

/** The point `length` px from `from` in the direction of `to`, on an axis-aligned segment. */
function towards(from: Point, to: Point, length: number): Point {
  return {
    x: from.x + Math.sign(to.x - from.x) * length,
    y: from.y + Math.sign(to.y - from.y) * length,
  };
}

/** Two decimals keep the path short without visible rounding. */
function fmt(value: number): string {
  return String(Math.round(value * 100) / 100);
}
