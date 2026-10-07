import { useEffect, useId, useState } from "react";
import { Search } from "lucide-react";
import { api } from "../lib/api";

export default function ArticlePicker({
  label,
  value,
  selected,
  onChange,
  disabled,
}: {
  label: string;
  value: string;
  selected: string | null;
  onChange: (text: string, selected: string | null) => void;
  disabled: boolean;
}) {
  const id = useId();
  const [results, setResults] = useState<string[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    setResults([]);
    setActive(-1);
    setError("");
    setLoading(false);
    if (!open || selected === value || !value.trim()) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      setLoading(true);
      void api
        .search(value.trim(), controller.signal)
        .then((data) => {
          if (!controller.signal.aborted)
            setResults(data.results.map((result) => result.title));
        })
        .catch((e) => {
          if (!controller.signal.aborted) setError((e as Error).message);
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 300);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [value, selected, open]);
  const choose = (title: string) => {
    onChange(title, title);
    setOpen(false);
  };
  return (
    <div className="article-picker">
      <label htmlFor={id}>{label}</label>
      <div className="search-field">
        <Search size={16} aria-hidden="true" />
        <input
          id={id}
          role="combobox"
          aria-expanded={open && results.length > 0}
          aria-controls={id + "-results"}
          aria-autocomplete="list"
          aria-activedescendant={
            active >= 0 ? id + "-option-" + active : undefined
          }
          autoComplete="off"
          maxLength={300}
          value={value}
          disabled={disabled}
          placeholder="Search Wikipedia"
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onChange={(e) => {
            onChange(e.target.value, null);
            setOpen(true);
          }}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setOpen(true);
              setActive((n) => Math.min(n + 1, results.length - 1));
            }
            if (e.key === "ArrowUp") {
              e.preventDefault();
              setActive((n) => (results.length ? Math.max(0, n - 1) : -1));
            }
            if (e.key === "Escape") setOpen(false);
            if (e.key === "Enter" && open && active >= 0 && results[active]) {
              e.preventDefault();
              choose(results[active]);
            }
          }}
        />
      </div>
      {open && results.length > 0 && (
        <ul
          id={id + "-results"}
          role="listbox"
          aria-label={label + " suggestions"}
          className="suggestions"
        >
          {results.map((title, index) => (
            <li
              role="option"
              aria-selected={active === index}
              key={title}
              id={id + "-option-" + index}
            >
              <button
                type="button"
                tabIndex={-1}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(title)}
              >
                {title}
              </button>
            </li>
          ))}
        </ul>
      )}
      {open && loading && <small role="status">Searching...</small>}
      {open && error && <small role="alert">{error}</small>}
      {open &&
        !loading &&
        !error &&
        value &&
        selected !== value &&
        results.length === 0 && <small>No matches selected</small>}
    </div>
  );
}
