package net.conczin.mca.client.gui;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FamilyTreeTransition {
    private final Map<UUID, FamilyTreeLayout.Bounds> previousCards = new HashMap<>();
    private final Map<UUID, FamilyTreeLayout.Bounds> revealStarts = new HashMap<>();
    private final FamilyTreeLayout.Result target;
    private final int shiftX;
    private final int shiftY;

    FamilyTreeTransition(FamilyTreeLayout.Result previous, FamilyTreeLayout.Result target, UUID anchor) {
        this(previous, target, anchor, 1.0F);
    }

    FamilyTreeTransition(FamilyTreeLayout.Result previous, FamilyTreeLayout.Result target,
                         UUID anchor, float zoom) {
        for (FamilyTreeLayout.Card card : previous.cards()) {
            previousCards.put(card.uuid(), card.bounds());
        }
        this.target = target;
        FamilyTreeLayout.Bounds oldAnchor = previousCards.get(anchor);
        FamilyTreeLayout.Bounds newAnchor = target.cards().stream()
                .filter(card -> card.uuid().equals(anchor))
                .map(FamilyTreeLayout.Card::bounds)
                .findFirst().orElse(null);
        shiftX = oldAnchor == null || newAnchor == null ? 0 : oldAnchor.centerX() - newAnchor.centerX();
        shiftY = oldAnchor == null || newAnchor == null ? 0 : oldAnchor.centerY() - newAnchor.centerY();
        Map<UUID, FamilyTreeLayout.Bounds> targetCards = new HashMap<>();
        for (FamilyTreeLayout.Card card : target.cards()) {
            FamilyTreeLayout.Bounds end = translated(card.bounds(), shiftX, shiftY);
            targetCards.put(card.uuid(), end);
            if (previousCards.containsKey(card.uuid())) {
                continue;
            }
            for (FamilyTreeLayout.Edge edge : target.edges()) {
                UUID relative = edge.from().equals(card.uuid()) ? edge.to()
                        : edge.to().equals(card.uuid()) ? edge.from() : null;
                FamilyTreeLayout.Bounds known = previousCards.get(relative);
                if (known == null) {
                    continue;
                }
                int dx = revealOffset(known.centerX() - end.centerX(), zoom);
                int dy = revealOffset(known.centerY() - end.centerY(), zoom);
                revealStarts.put(card.uuid(), translated(end, dx, dy));
                break;
            }
        }
        // Carry the entrance motion through newly loaded relatives, not just the first visible hop.
        Map<UUID, List<UUID>> neighbors = new HashMap<>();
        for (FamilyTreeLayout.Edge edge : target.edges()) {
            neighbors.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to());
            neighbors.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge.from());
        }
        ArrayDeque<UUID> pending = new ArrayDeque<>(revealStarts.keySet());
        while (!pending.isEmpty()) {
            UUID animated = pending.removeFirst();
            FamilyTreeLayout.Bounds start = revealStarts.get(animated);
            FamilyTreeLayout.Bounds end = targetCards.get(animated);
            if (end == null) {
                continue;
            }
            for (UUID relative : neighbors.getOrDefault(animated, List.of())) {
                if (previousCards.containsKey(relative) || revealStarts.containsKey(relative)) {
                    continue;
                }
                FamilyTreeLayout.Bounds relativeEnd = targetCards.get(relative);
                if (relativeEnd != null) {
                    revealStarts.put(relative, translated(relativeEnd,
                            start.left() - end.left(), start.top() - end.top()));
                    pending.addLast(relative);
                }
            }
        }
    }

    FamilyTreeLayout.Result at(double progress) {
        double clamped = Math.max(0, Math.min(1, progress));
        double eased = clamped * clamped * (3.0 - 2.0 * clamped);
        List<FamilyTreeLayout.Card> cards = new ArrayList<>(target.cards().size());
        FamilyTreeLayout.Bounds bounds = null;
        for (FamilyTreeLayout.Card card : target.cards()) {
            FamilyTreeLayout.Bounds end = translated(card.bounds(), shiftX, shiftY);
            FamilyTreeLayout.Bounds start = previousCards.getOrDefault(card.uuid(),
                    revealStarts.getOrDefault(card.uuid(), end));
            FamilyTreeLayout.Bounds current = new FamilyTreeLayout.Bounds(
                    lerp(start.left(), end.left(), eased),
                    lerp(start.right(), end.right(), eased),
                    lerp(start.top(), end.top(), eased),
                    lerp(start.bottom(), end.bottom(), eased)
            );
            cards.add(new FamilyTreeLayout.Card(card.uuid(), current));
            bounds = bounds == null ? current : bounds.union(current);
        }
        return new FamilyTreeLayout.Result(cards, target.edges(),
                bounds == null ? new FamilyTreeLayout.Bounds(0, 0, 0, 0) : bounds, target.connectors());
    }

    FamilyTreeViewModel.ViewportState settledViewport(FamilyTreeViewModel.ViewportState viewport) {
        return new FamilyTreeViewModel.ViewportState(
                viewport.panX() + shiftX * viewport.zoom(),
                viewport.panY() + shiftY * viewport.zoom(),
                viewport.zoom()
        );
    }

    private static int lerp(int from, int to, double fraction) {
        return (int) Math.round(from + (to - from) * fraction);
    }

    private static int revealOffset(int displacement, float zoom) {
        if (displacement == 0) {
            return 0;
        }
        // Maintain readable motion when zooming out without changing card/text geometry.
        double safeZoom = Float.isFinite(zoom) && zoom > 0 ? zoom : 1.0;
        int worldDistance = (int) Math.ceil(Math.max(12.0 / safeZoom,
                32.0 / Math.max(1.0, safeZoom)));
        return displacement < 0 ? -worldDistance : worldDistance;
    }

    private static FamilyTreeLayout.Bounds translated(FamilyTreeLayout.Bounds bounds, int dx, int dy) {
        return new FamilyTreeLayout.Bounds(bounds.left() + dx, bounds.right() + dx,
                bounds.top() + dy, bounds.bottom() + dy);
    }
}
