import { useEffect, useState } from "react";
import { createPortal } from "react-dom";

/**
 * What to mark: the element `selector` matches, or the innermost element whose text is exactly `text`. `closest`
 * then moves up to an ancestor, such as the card or section around a heading.
 */
export interface Mark {
  selector?: string;
  text?: string;
  closest?: string;
  label?: string;
}

interface Box {
  key: string;
  label?: string;
  left: number;
  top: number;
  width: number;
  height: number;
}

declare global {
  interface Window {
    screenshotMarks?: { show: (marks: Mark[]) => void; clear: () => void };
  }
}

const PAD = 6;
const LABEL_HEIGHT = 22;

function target(mark: Mark): Element | null {
  let found: Element | null = null;
  if (mark.text !== undefined) {
    const matches = [...document.querySelectorAll(mark.selector ?? "*")].filter(
      (el) =>
        !el.closest("[data-screenshot-marks]") &&
        el.textContent?.trim() === mark.text,
    );
    found =
      matches.find(
        (el) => !matches.some((other) => other !== el && el.contains(other)),
      ) ?? null;
  } else if (mark.selector) {
    found = document.querySelector(mark.selector);
  }
  return found && mark.closest ? found.closest(mark.closest) : found;
}

function measure(marks: Mark[]): Box[] {
  return marks.flatMap((mark, index) => {
    const rect = target(mark)?.getBoundingClientRect();
    if (!rect) return [];
    return [
      {
        key: `${index}`,
        label: mark.label,
        left: rect.left + window.scrollX - PAD,
        top: rect.top + window.scrollY - PAD,
        width: rect.width + PAD * 2,
        height: rect.height + PAD * 2,
      },
    ];
  });
}

const same = (a: Box[], b: Box[]) => JSON.stringify(a) === JSON.stringify(b);

/**
 * Red boxes and labels for report screenshots, driven from the browser console or Playwright through
 * `window.screenshotMarks`. Boxes are measured again every frame, so they follow scrolling and re-renders.
 */
export function ScreenshotMarks() {
  const [marks, setMarks] = useState<Mark[]>([]);
  const [boxes, setBoxes] = useState<Box[]>([]);

  useEffect(() => {
    window.screenshotMarks = { show: setMarks, clear: () => setMarks([]) };
    return () => {
      delete window.screenshotMarks;
    };
  }, []);

  useEffect(() => {
    if (marks.length === 0) {
      setBoxes([]);
      return;
    }
    let frame = 0;
    const update = () => {
      const next = measure(marks);
      setBoxes((current) => (same(current, next) ? current : next));
      frame = requestAnimationFrame(update);
    };
    update();
    return () => cancelAnimationFrame(frame);
  }, [marks]);

  if (boxes.length === 0) return null;
  return createPortal(
    <div data-screenshot-marks aria-hidden="true">
      {boxes.map((box) => {
        const labelAbove = box.top - LABEL_HEIGHT >= window.scrollY;
        return (
          <div key={box.key}>
            <div
              className="pointer-events-none absolute z-50 rounded-lg border-3 border-brand-red"
              style={{
                left: box.left,
                top: box.top,
                width: box.width,
                height: box.height,
              }}
            />
            {box.label && (
              <div
                data-mark-label
                className={`pointer-events-none absolute z-50 whitespace-nowrap bg-brand-red px-2 font-semibold text-white text-xs leading-[22px] ${labelAbove ? "-translate-y-full rounded-t-md" : "rounded-b-md"}`}
                style={{
                  right:
                    document.documentElement.clientWidth -
                    (box.left + box.width) +
                    8,
                  top: labelAbove ? box.top : box.top + box.height,
                }}
              >
                {box.label}
              </div>
            )}
          </div>
        );
      })}
    </div>,
    document.body,
  );
}
