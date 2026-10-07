package com.wikirace.game.model;

import java.util.ArrayList;
import java.util.List;
import com.wikirace.exception.GameException;
import static com.wikirace.exception.GameException.Code.NO_BACK_HISTORY;

public class NavigationHistory {
    private final List<String> entries = new ArrayList<>();
    private int cursor;

    public NavigationHistory(String startArticle) { entries.add(startArticle); }
    public String current() { return entries.get(cursor); }
    public boolean canGoBack() { return cursor > 0; }
    public int cursor() { return cursor; }
    public List<String> entries() { return List.copyOf(entries); }

    public String previous() {
        if (!canGoBack()) throw new GameException(NO_BACK_HISTORY, "No previous article exists.");
        return entries.get(cursor - 1);
    }

    public void navigate(String destination) {
        entries.subList(cursor + 1, entries.size()).clear();
        entries.add(destination);
        cursor++;
    }

    public void back() {
        previous();
        cursor--;
    }
}
