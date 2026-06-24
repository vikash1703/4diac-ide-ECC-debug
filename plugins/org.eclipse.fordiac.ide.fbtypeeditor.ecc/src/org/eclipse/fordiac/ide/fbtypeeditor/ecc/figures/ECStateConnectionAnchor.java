package org.eclipse.fordiac.ide.fbtypeeditor.ecc.figures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.draw2d.AbstractConnectionAnchor;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.fordiac.ide.model.libraryElement.ECState;
import org.eclipse.fordiac.ide.model.libraryElement.ECTransition;

public class ECStateConnectionAnchor extends AbstractConnectionAnchor {

	private enum Edge {
		TOP, BOTTOM, LEFT, RIGHT
	}

	private final ECState state;
	private final ECTransition transition;
	private static final int SAME_EDGE_GAP = 15;// check by giving different values it changes the width between
												// transition points

	public ECStateConnectionAnchor(final IFigure owner, final ECState state, final ECTransition transition) {
		super(owner);
		this.state = state;
		this.transition = transition;
	}

	/**
	 * Returns the anchor point on the state border closest to the given reference
	 * point.
	 */
	@Override
	public Point getLocation(final Point reference) {
		final Rectangle bounds = getOwner().getBounds().getCopy();
		getOwner().translateToAbsolute(bounds);

		final Point center = bounds.getCenter();
		final Point ref = (reference != null) ? reference : center;

		final Edge edge = edgeForReference(center, ref);

		return switch (edge) {
		case TOP -> applySpacing(bounds, bounds.getTop(), true, edge);
		case BOTTOM -> applySpacing(bounds, bounds.getBottom(), true, edge);
		case LEFT -> applySpacing(bounds, bounds.getLeft(), false, edge);
		case RIGHT -> applySpacing(bounds, bounds.getRight(), false, edge);
		};
	}

	/**
	 * Determines which edge of this state the connection should attach to based on
	 * the reference point direction.
	 */
	private Edge edgeForReference(final Point center, final Point ref) {
		final int dx = ref.x - center.x;
		final int dy = ref.y - center.y;

		final boolean hasAction = (state != null) && !state.getECAction().isEmpty();

		if (Math.abs(dy) > Math.abs(dx)) {
			return (dy >= 0) ? Edge.BOTTOM : Edge.TOP;
		}

		if (dx >= 0) {
			return hasAction ? Edge.BOTTOM : Edge.RIGHT;
		}
		return Edge.LEFT;
	}

	/** Spreads multiple transitions on the same edge so they do not overlap. */
	private Point applySpacing(final Rectangle bounds, final Point base, final boolean verticalEdge, final Edge edge) {
		final List<ECTransition> ordered = getTransitionsOnEdgeOrdered(edge);
		final int index = indexOf(ordered, transition);
		final int count = Math.max(1, ordered.size());

		final double centered = index - (count - 1) / 2.0;
		final int delta = (int) Math.round(centered * SAME_EDGE_GAP);

		final Point p = verticalEdge ? base.getTranslated(delta, 0) : base.getTranslated(0, delta);

		final int margin = 4;// you can change here to test.
		if (verticalEdge) {
			final int minX = bounds.x + margin;
			final int maxX = bounds.x + bounds.width - margin;
			p.x = Math.max(minX, Math.min(maxX, p.x));
		} else {
			final int minY = bounds.y + margin;
			final int maxY = bounds.y + bounds.height - margin;
			p.y = Math.max(minY, Math.min(maxY, p.y));
		}
		return p;
	}

	/**
	 * Returns all transitions on the given edge sorted by angle for consistent
	 * ordering.
	 */
	private List<ECTransition> getTransitionsOnEdgeOrdered(final Edge edge) {
		final List<ECTransition> result = new ArrayList<>();
		if (state == null) {
			return result;
		}

		final List<ECTransition> list = (transition != null && transition.getSource() == state)
				? state.getOutTransitions()
				: state.getInTransitions();

		for (final ECTransition t : list) {
			if (edgeForTransition(t) == edge) {
				result.add(t);
			}
		}

		result.sort(Comparator.comparingDouble(this::angleForTransitionNormalized));
		return result;
	}

	/** Returns the index of the given transition in the list. */
	private static int indexOf(final List<ECTransition> list, final ECTransition t) {
		if (t == null) {
			return 0;
		}
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == t) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * Determines which edge of this state the given transition attaches to using
	 * figure bounds.
	 */
	private Edge edgeForTransition(final ECTransition t) {
		if (t == null || state == null) {
			return Edge.RIGHT;
		}
		final ECState other = (t.getSource() == state) ? t.getDestination() : t.getSource();
		if (other == null) {
			return Edge.RIGHT;
		}

		final Rectangle bounds = getOwner().getBounds().getCopy();
		getOwner().translateToAbsolute(bounds);
		final Point stateCenter = bounds.getCenter();

		final Point otherScreen = other.getPosition().toScreenPoint();

		final double dx = otherScreen.x - stateCenter.x;
		final double dy = otherScreen.y - stateCenter.y;

		final boolean hasAction = !state.getECAction().isEmpty();

		if (Math.abs(dy) > Math.abs(dx)) {
			return (dy >= 0) ? Edge.BOTTOM : Edge.TOP;
		}
		if (dx >= 0) {
			return hasAction ? Edge.BOTTOM : Edge.RIGHT;
		}
		return Edge.LEFT;
	}

	/**
	 * Returns the normalized angle from this state to the other state of the
	 * transition.
	 */
	private double angleForTransitionNormalized(final ECTransition t) {
		if (t == null || state == null) {
			return 0.0;
		}
		final ECState other = (t.getSource() == state) ? t.getDestination() : t.getSource();

		final double dx = other.getPosition().getX() - state.getPosition().getX();
		final double dy = other.getPosition().getY() - state.getPosition().getY();

		double angle = Math.atan2(dy, dx);
		if (angle < 0) {
			angle += Math.PI * 2.0;
		}
		return angle;
	}
}
