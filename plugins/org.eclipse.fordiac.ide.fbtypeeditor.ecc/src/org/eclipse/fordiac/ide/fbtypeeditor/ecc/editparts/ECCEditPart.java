/*******************************************************************************
 * Copyright (c) 2008 Profactor GmbH, TU Wien ACIN, fortiss GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Gerhard Ebenhofer, Alois Zoitl
 *     - initial API and implementation and/or initial documentation
 *   Vikash Kumar Sinha
 *     - create a state and start renaming on canvas double-click
 *******************************************************************************/
package org.eclipse.fordiac.ide.fbtypeeditor.ecc.editparts;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.ConnectionRouter;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.fordiac.ide.fbtypeeditor.ecc.commands.CreateECStateCommand;
import org.eclipse.fordiac.ide.fbtypeeditor.ecc.editors.StateCreationFactory;
import org.eclipse.fordiac.ide.fbtypeeditor.ecc.figures.ECCTransitionRouter;
import org.eclipse.fordiac.ide.fbtypeeditor.ecc.policies.ECCXYLayoutEditPolicy;
import org.eclipse.fordiac.ide.gef.editparts.AbstractDiagramEditPart;
import org.eclipse.fordiac.ide.gef.editparts.LabelDirectEditManager;
import org.eclipse.fordiac.ide.model.CoordinateConverter;
import org.eclipse.fordiac.ide.model.libraryElement.ECC;
import org.eclipse.fordiac.ide.model.libraryElement.ECState;
import org.eclipse.fordiac.ide.model.libraryElement.Position;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.Request;
import org.eclipse.gef.RequestConstants;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.editpolicies.RootComponentEditPolicy;
import org.eclipse.gef.requests.SelectionRequest;
import org.eclipse.gef.tools.DirectEditManager;
import org.eclipse.swt.widgets.Display;

public class ECCEditPart extends AbstractDiagramEditPart {

	/** The adapter. */
	private Adapter adapter;

	@Override
	public void activate() {
		if (!isActive()) {
			super.activate();
			((Notifier) getModel()).eAdapters().add(getContentAdapter());
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.gef.editparts.AbstractGraphicalEditPart#deactivate()
	 */
	@Override
	public void deactivate() {
		if (isActive()) {
			super.deactivate();
			((Notifier) getModel()).eAdapters().remove(getContentAdapter());
		}
	}

	/**
	 * Gets the content adapter.
	 *
	 * @return the content adapter
	 */
	public Adapter getContentAdapter() {
		if (null == adapter) {
			adapter = new AdapterImpl() {
				@Override
				public void notifyChanged(final Notification notification) {
					final int type = notification.getEventType();
					switch (type) {
					case Notification.ADD, Notification.ADD_MANY, Notification.REMOVE, Notification.REMOVE_MANY:
						refreshChildren();
						break;
					default:
						break;
					}
				}
			};
		}
		return adapter;
	}

	/**
	 * Creates the EditPolicies used for this EditPart.
	 *
	 * @see org.eclipse.gef.editparts.AbstractEditPart#createEditPolicies()
	 */
	@Override
	protected void createEditPolicies() {
		installEditPolicy(EditPolicy.COMPONENT_ROLE, new RootComponentEditPolicy());
		// handles constraint changes of model elements and creation of new
		// model elements
		installEditPolicy(EditPolicy.LAYOUT_ROLE, new ECCXYLayoutEditPolicy());

	}

	@Override
	public void performRequest(final Request request) {
		if (request.getType() == RequestConstants.REQ_OPEN
				&& request instanceof final SelectionRequest selectionRequest) {
			createStateAndDirectEdit(selectionRequest);
		} else {
			super.performRequest(request);
		}
	}

	public void createStateAndDirectEdit(final SelectionRequest request) {
		final ECState newState = (ECState) new StateCreationFactory().getNewObject();
		final Point location = request.getLocation().getCopy();
		getFigure().translateToRelative(location);
		final Position pos = CoordinateConverter.INSTANCE.createPosFromScreenCoordinates(location.x, location.y);
		final CreateECStateCommand cmd = new CreateECStateCommand(newState, pos, getCastedECCModel());
		getViewer().getEditDomain().getCommandStack().execute(cmd);
		Display.getDefault().asyncExec(() -> {
			if (getViewer() == null) {
				return;
			}
			if (getViewer().getEditPartRegistry().get(newState) instanceof final ECStateEditPart stateEditPart) {
				getViewer().select(stateEditPart);
				directEditNewState(stateEditPart);
			}
		});
	}

	private void directEditNewState(final ECStateEditPart stateEditPart) {
		final CommandStack commandStack = getViewer().getEditDomain().getCommandStack();
		final DirectEditManager manager = new LabelDirectEditManager(stateEditPart, stateEditPart.getNameLabel()) {
			private boolean nameEntered;

			@Override
			protected void commit() {
				nameEntered = getCellEditor() != null && getCellEditor().isDirty();
				super.commit();
			}

			@Override
			protected void bringDown() {
				super.bringDown();
				if (!nameEntered) {
					nameEntered = true;
					if (commandStack.canUndo()) {
						commandStack.undo();
					}
				}
			}
		};
		manager.show();
	}

	/**
	 * returns the model object as <code>ECC</code>.
	 *
	 * @return ECC to be visualized
	 */
	public ECC getCastedECCModel() {
		return (ECC) getModel();
	}

	/**
	 * Returns the children of the FBNetwork.
	 *
	 * @return the list of children s
	 *
	 * @see org.eclipse.gef.editparts.AbstractEditPart#getModelChildren()
	 */
	@Override
	protected List<?> getModelChildren() {
		final List<ECState> temp = new ArrayList<>();
		temp.addAll(getCastedECCModel().getECState());
		return temp;
	}

	@Override
	protected ConnectionRouter createConnectionRouter(final IFigure figure) {
		return new ECCTransitionRouter();
	}
}
