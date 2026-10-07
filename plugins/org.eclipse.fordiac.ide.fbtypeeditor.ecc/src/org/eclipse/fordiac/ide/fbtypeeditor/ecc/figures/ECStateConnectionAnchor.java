/*******************************************************************************
 * Copyright (c) 2026 Vikash Kumar Sinha
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Vikash Kumar Sinha - initial implementation
*******************************************************************************/
package org.eclipse.fordiac.ide.fbtypeeditor.ecc.figures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.draw2d.AbstractConnectionAnchor;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.fordiac.ide.model.CoordinateConverter;
import org.eclipse.fordiac.ide.model.libraryElement.ECState;
import org.eclipse.fordiac.ide.model.libraryElement.ECTransition;

public class ECStateConnectionAnchor extends AbstractConnectionAnchor {

	private final ECState state;
	private final ECTransition transition;
	private final boolean sourceEnd;

	public ECStateConnectionAnchor(final ECStateFigure owner, final ECState state, final ECTransition transition,
			final boolean sourceEnd) {
		super(owner);
		this.state = state;
		this.transition = transition;
		this.sourceEnd = sourceEnd;
	}

	private record Endpoint(ECTransition transition, boolean sourceEnd) {
	}

	@Override
	public ECStateFigure getOwner() {
		return (ECStateFigure) super.getOwner();
	}

	ECTransition getTransition() {
		return transition;
	}

	@Override
	public Point getReferencePoint() {
		if (transition != null && transition.getPosition() != null) {
			return bendPoint(transition);
		}
		final Rectangle bounds = getOwner().getBounds().getCopy();
		getOwner().translateToAbsolute(bounds);
		return bounds.getCenter();
	}

	@Override
	public Point getLocation(final Point reference) {
		final Rectangle nameBounds = getOwner().getNameLabel().getBounds().getCopy();
		getOwner().getNameLabel().translateToAbsolute(nameBounds);

		final Point ref = (reference != null) ? reference : nameBounds.getCenter();

		return applySpacing(nameBounds, edgeOf(transition, sourceEnd, ref, nameBounds));
	}

	EdgeDirection getEdgeForReference(final Point reference) {
		final Rectangle nameBounds = getOwner().getNameLabel().getBounds().getCopy();
		getOwner().getNameLabel().translateToAbsolute(nameBounds);
		return edgeOf(transition, sourceEnd, reference, nameBounds);
	}

	private EdgeDirection edgeOf(final ECTransition ecTransition, final boolean isSourceEnd, final Point reference,
			final Rectangle nameBounds) {
		if (isSelfTransition(ecTransition) && ecTransition.getPosition() != null) {
			return EdgeDirection.selfLoopEdge(bendPoint(ecTransition), nameBounds, isSourceEnd);
		}
		return EdgeDirection.of(reference, nameBounds, state);
	}

	private Point applySpacing(final Rectangle bounds, final EdgeDirection edge) {
		final List<Endpoint> ordered = new ArrayList<>(endpointsOnEdge(edge, bounds));
		final Endpoint current = new Endpoint(transition, sourceEnd);

		if (transition != null && !ordered.contains(current)) {
			ordered.add(current);
			ordered.sort(comparator(edge));
		}

		final int index = transition == null ? 0 : Math.max(0, ordered.indexOf(current));
		final int count = Math.max(1, ordered.size());

		return switch (edge) {
		case TOP -> {
			final int slotWidth = Math.max(1, bounds.width / (count + 1));
			yield new Point(bounds.x + (index + 1) * slotWidth, bounds.y);
		}
		case BOTTOM -> {
			final int slotWidth = Math.max(1, bounds.width / (count + 1));
			yield new Point(bounds.x + (index + 1) * slotWidth, bounds.y + bounds.height);
		}
		case LEFT -> {
			final int slotHeight = Math.max(1, bounds.height / (count + 1));
			yield new Point(bounds.x, bounds.y + (index + 1) * slotHeight);
		}
		case RIGHT -> {
			final int slotHeight = Math.max(1, bounds.height / (count + 1));
			yield new Point(bounds.x + bounds.width, bounds.y + (index + 1) * slotHeight);
		}
		};
	}

	private List<Endpoint> endpointsOnEdge(final EdgeDirection edge, final Rectangle nameBounds) {
		if (state == null) {
			return List.of();
		}

		return Stream
				.concat(state.getOutTransitions().stream().map(t -> new Endpoint(t, true)),
						state.getInTransitions().stream().map(t -> new Endpoint(t, false)))
				.filter(e -> e.transition() != null).filter(e -> e.transition().getPosition() != null).filter(e -> {
					final EdgeDirection effectiveEdge = ECCTransitionRouter.getEffectiveEdge(e.transition(),
							e.sourceEnd());

					if (effectiveEdge != null) {
						return effectiveEdge == edge;
					}

					return edgeOf(e.transition(), e.sourceEnd(), bendPoint(e.transition()), nameBounds) == edge;
				}).distinct().sorted(comparator(edge)).collect(Collectors.toCollection(ArrayList::new));
	}

	private Comparator<Endpoint> comparator(final EdgeDirection edge) {
		final boolean horizontal = (edge == EdgeDirection.TOP) || (edge == EdgeDirection.BOTTOM);

		return Comparator
				.<Endpoint>comparingInt(e -> horizontal ? bendPoint(e.transition()).x : bendPoint(e.transition()).y)
				.thenComparing(Endpoint::sourceEnd);
	}

	private static boolean isSelfTransition(final ECTransition ecTransition) {
		return ecTransition != null && ecTransition.getSource() != null
				&& ecTransition.getSource() == ecTransition.getDestination();
	}

	private Point bendPoint(final ECTransition ecTransition) {
		final Point p = CoordinateConverter.INSTANCE.toScreenPoint(ecTransition.getPosition());
		getOwner().translateToAbsolute(p);
		return p;
	}
}
