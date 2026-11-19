import { type PointerEvent, useEffect, useId, useState } from "react";
import { createPortal } from "react-dom";

interface Point {
  x: number;
  y: number;
}

const MIN_SIZE = 8;

function box(start: Point, end: Point) {
  return {
    left: Math.min(start.x, end.x),
    top: Math.min(start.y, end.y),
    width: Math.abs(end.x - start.x),
    height: Math.abs(end.y - start.y),
  };
}

const point = (e: PointerEvent) => ({ x: e.clientX, y: e.clientY });

export function NotesTab() {
  const noteId = useId();
  const [note, setNote] = useState("");
  const [drawing, setDrawing] = useState(false);
  const [drag, setDrag] = useState<{ start: Point; end: Point } | null>(null);

  useEffect(() => {
    if (!drawing) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      setDrawing(false);
      setDrag(null);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [drawing]);

  function finish(end: Point) {
    if (drag) {
      const area = box(drag.start, end);
      if (area.width >= MIN_SIZE && area.height >= MIN_SIZE) {
        window.screenshotMarks?.add({
          area: {
            ...area,
            left: area.left + window.scrollX,
            top: area.top + window.scrollY,
          },
          label: note.trim() || undefined,
        });
        setNote("");
      }
    }
    setDrawing(false);
    setDrag(null);
  }

  return (
    <div>
      <label htmlFor={noteId} className="block font-semibold text-sm">
        Label
      </label>
      <input
        id={noteId}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Optional"
        className="mt-1 w-full rounded-lg border border-line px-3 py-2 text-sm"
      />
      <div className="mt-3 flex gap-2">
        <button
          type="button"
          onClick={() => setDrawing(true)}
          className="rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white hover:opacity-90"
        >
          Draw a box
        </button>
        <button
          type="button"
          onClick={() => window.screenshotMarks?.clearAreas()}
          className="rounded-lg border border-line px-4 py-2 text-sm hover:bg-mist"
        >
          Clear boxes
        </button>
      </div>
      {drawing &&
        createPortal(
          <div
            data-screenshot-marks
            className="fixed inset-0 z-50 cursor-crosshair touch-none"
            onPointerDown={(e) => {
              e.currentTarget.setPointerCapture(e.pointerId);
              setDrag({ start: point(e), end: point(e) });
            }}
            onPointerMove={(e) => {
              if (drag) setDrag({ ...drag, end: point(e) });
            }}
            onPointerUp={(e) => finish(point(e))}
          >
            <p className="pointer-events-none fixed top-4 left-1/2 -translate-x-1/2 rounded-md bg-ink px-3 py-1.5 font-inter text-sm text-white shadow-lg">
              Drag to draw a box. Esc cancels.
            </p>
            {drag && (
              <div
                className="pointer-events-none fixed rounded-lg border-3 border-brand-red"
                style={box(drag.start, drag.end)}
              />
            )}
          </div>,
          document.body,
        )}
    </div>
  );
}
