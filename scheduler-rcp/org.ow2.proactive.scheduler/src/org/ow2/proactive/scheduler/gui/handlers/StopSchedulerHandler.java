/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.gui.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.HandlerEvent;
import org.ow2.proactive.scheduler.gui.actions.SchedulerGUIAction;
import org.ow2.proactive.scheduler.gui.actions.StopSchedulerAction;

public class StopSchedulerHandler extends SchedulerGUIAbstractHandler {
	@Override
	protected SchedulerGUIAction createAction() {
		StopSchedulerAction action = new StopSchedulerAction();
		action.setEnabled(false);
		return action;
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		getAction().run();
		this.setBaseEnabled(false);
		fireHandlerChanged(new HandlerEvent(this, true, false));
		return null;
	}

	
	@Override
	public void setEnabled(Object context)
	{
		setBaseEnabled(getAction().isEnabled());
	}
	
}