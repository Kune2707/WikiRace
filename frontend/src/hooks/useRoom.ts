import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "../lib/api";
import { connectRoom } from "../realtime/roomStream";
import type {
  Article,
  Identity,
  Intent,
  Session,
  View,
  RoomEvent,
} from "../lib/types";

export function useRoom() {
  const [identity, setIdentity] = useState<Identity | null>(null);
  const [view, setView] = useState<View | null>(null);
  const current = useRef<View | null>(null);
  const identityRef = useRef<Identity | null>(null);
  const sessionGeneration = useRef(0);
  const [error, setError] = useState("");
  const [connected, setConnected] = useState(false);
  const transportConnected = useRef(false);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [pending, setPending] = useState<Intent | null>(null);
  const pendingRef = useRef<Intent | null>(null);
  const updatePending = (intent: Intent | null) => {
    pendingRef.current = intent;
    setPending(intent);
  };
  const [article, setArticle] = useState<Article | null>(null);
  const [articleError, setArticleError] = useState("");
  const [articleAttempt, setArticleAttempt] = useState(0);
  const anchor = useRef({ server: Date.now(), local: performance.now() });
  const [now, setNow] = useState(Date.now());

  const accept = useCallback((incoming: View, id: Identity) => {
    if (
      identityRef.current !== id ||
      incoming.room.roomCode !== id.code ||
      incoming.me.playerId !== id.playerId
    )
      return;
    const previous = current.current;
    if (previous && incoming.me.movementRevision < previous.me.movementRevision)
      return;
    if (previous && incoming.room.version < previous.room.version)
      incoming = {
        ...incoming,
        room: previous.room,
        serverTime: previous.serverTime,
      };
    current.current = incoming;
    anchor.current = {
      server: Date.parse(incoming.serverTime),
      local: performance.now(),
    };
    setNow(anchor.current.server);
    setView(incoming);
  }, []);

  const attach = useCallback(
    (id: Identity, initial?: View) => {
      sessionGeneration.current++;
      identityRef.current = id;
      current.current = null;
      lock.current = false;
      setBusy(false);
      setIdentity(id);
      setConnected(false);
      transportConnected.current = false;
      setView(null);
      setArticle(null);
      updatePending(null);
      setError("");
      try {
        const credentials = JSON.stringify({ code: id.code, token: id.token });
        sessionStorage.setItem("wikirace.session", credentials);
        localStorage.setItem("wikirace.room." + id.code, credentials);
      } catch {
        /* In-memory play still works. */
      }
      if (initial) accept(initial, id);
    },
    [accept],
  );
  const resume = async (credentials: Identity, signal?: AbortSignal) => {
    if (lock.current) return;
    const generation = ++sessionGeneration.current;
    let attached = false;
    setBusy(true);
    setError("");
    try {
      const restored = await api.room(credentials, signal);
      if (!signal?.aborted && sessionGeneration.current === generation) {
        attach(
          {
            code: credentials.code,
            token: credentials.token,
            playerId: restored.me.playerId,
          },
          restored,
        );
        attached = true;
      }
    } catch (e) {
      if (!signal?.aborted && sessionGeneration.current === generation) {
        setError((e as Error).message);
        if (
          e instanceof ApiError &&
          ["ROOM_NOT_FOUND", "INVALID_PLAYER_TOKEN"].includes(e.code)
        ) {
          try {
            localStorage.removeItem("wikirace.room." + credentials.code);
            sessionStorage.removeItem("wikirace.session");
          } catch {
            /* Storage is optional. */
          }
        }
      }
    } finally {
      if (
        (attached || sessionGeneration.current === generation)
      )
        setBusy(false);
    }
  };
  const enter = async (name: string, code?: string) => {
    if (lock.current) return null;
    lock.current = true;
    const generation = ++sessionGeneration.current;
    let attached = false;
    setBusy(true);
    setError("");
    try {
      const session: Session = await api.enter(name, code);
      if (sessionGeneration.current !== generation) return null;
      attach(
        {
          code: session.view.room.roomCode,
          token: session.playerSessionToken,
          playerId: session.playerId,
        },
        session.view,
      );
      attached = true;
      return session.view.room.roomCode;
    } catch (e) {
      if (sessionGeneration.current === generation) setError((e as Error).message);
      return null;
    } finally {
      if (attached || sessionGeneration.current === generation) {
        lock.current = false;
        setBusy(false);
      }
    }
  };

  const refresh = useCallback(
    async (id = identityRef.current, signal?: AbortSignal) => {
      if (!id) return;
      try {
        accept(await api.room(id, signal), id);
        if (!signal?.aborted && identityRef.current === id)
          setConnected(transportConnected.current);
      } catch (e) {
        if (!signal?.aborted && identityRef.current === id) {
          setConnected(false);
          setError((e as Error).message);
        }
      }
    },
    [accept],
  );

  useEffect(() => {
    if (!identity) return;
    const playerId = identity.playerId;
    const controller = new AbortController();
    let buffered: RoomEvent | null = null;
    let refreshing = false;
    let dirty = false;
    async function privateRefresh() {
      dirty = true;
      if (refreshing) return;
      refreshing = true;
      try {
        while (dirty && !controller.signal.aborted) {
          dirty = false;
          await refresh(identity, controller.signal);
          if (buffered && current.current) {
            const event = buffered;
            buffered = null;
            receive(event);
          }
        }
      } finally {
        refreshing = false;
      }
    }
    function receive(event: RoomEvent) {
      if (controller.signal.aborted || identityRef.current !== identity) return;
      const previous = current.current;
      if (!previous) {
        if (!buffered || buffered.version < event.version) buffered = event;
        return;
      }
      if (event.version <= previous.room.version) return;
      const next = {
        ...previous,
        room: event.room,
        serverTime: event.occurredAt,
      };
      current.current = next;
      anchor.current = {
        server: Date.parse(event.occurredAt),
        local: performance.now(),
      };
      setView(next);
      setNow(anchor.current.server);
      const own = event.room.players.find((p) => p.playerId === playerId);
      if (
        own?.clickCount !== previous.me.clickCount ||
        event.room.status !== previous.room.status
      )
        void privateRefresh();
    }
    const close = connectRoom(
      identity,
      receive,
      () => {
        if (controller.signal.aborted) return;
        transportConnected.current = true;
        setError("");
        void privateRefresh();
      },
      (message) => {
        if (!controller.signal.aborted) {
          transportConnected.current = false;
          setConnected(false);
          setError(message);
        }
      },
    );
    // Initial private state is useful while the authenticated transport is connecting.
    void privateRefresh();
    return () => {
      transportConnected.current = false;
      controller.abort();
      close();
    };
  }, [identity, refresh]);

  useEffect(() => {
    const interval = window.setInterval(
      () =>
        setNow(
          anchor.current.server + performance.now() - anchor.current.local,
        ),
      100,
    );
    return () => window.clearInterval(interval);
  }, []);

  const perform = async (command: string, body: unknown, intent?: Intent) => {
    const id = identityRef.current;
    if (
      !id ||
      lock.current ||
      (pendingRef.current && intent?.actionId !== pendingRef.current.actionId)
    )
      return false;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      if (intent) updatePending(intent);
      const incoming = intent
        ? (await api.move(id, intent)).view
        : await api.command(id, command, body);
      if (identityRef.current !== id) return false;
      accept(incoming, id);
      updatePending(null);
      return true;
    } catch (e) {
      if (identityRef.current !== id) return false;
      if (!(e instanceof ApiError) || !e.uncertain) updatePending(null);
      setError((e as Error).message);
      void refresh(id);
      return false;
    } finally {
      if (identityRef.current === id) {
        lock.current = false;
        setBusy(false);
      }
    }
  };

  const title = view?.me.currentArticle;
  const revision = view?.me.movementRevision;
  const readable =
    view && ["ACTIVE", "SUDDEN_DEATH", "FINISHED"].includes(view.room.status);
  useEffect(() => {
    if (!identity || !readable || !title) return;
    const controller = new AbortController();
    setArticle(null);
    setArticleError("");
    void api
      .article(identity, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        if (
          data.roomCode !== identity.code ||
          data.playerId !== identity.playerId ||
          data.movementRevision !== revision ||
          data.title !== title
        ) {
          setArticleError("Your location changed. Refresh article content.");
          void refresh(identity);
          return;
        }
        setArticle(data);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setArticleError((e as Error).message);
      });
    return () => controller.abort();
  }, [identity, readable, title, revision, articleAttempt, refresh]);

  const reset = () => {
    sessionGeneration.current++;
    transportConnected.current = false;
    lock.current = false;
    setBusy(false);
    identityRef.current = null;
    current.current = null;
    setIdentity(null);
    setView(null);
    setArticle(null);
    updatePending(null);
    setError("");
    try {
      sessionStorage.removeItem("wikirace.session");
    } catch {
      /* Storage can be unavailable. */
    }
  };
  return {
    identity,
    view,
    enter,
    attach,
    resume,
    reset,
    busy,
    connected,
    error,
    setError,
    pending,
    perform,
    refresh,
    now,
    article:
      article &&
      article.title === title &&
      article.movementRevision === revision
        ? article
        : null,
    articleError,
    retryArticle: () => setArticleAttempt((n) => n + 1),
  };
}
