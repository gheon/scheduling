/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2007 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@objectweb.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package functionalTests.component.migration;

import org.objectweb.proactive.ProActive;
import org.objectweb.proactive.core.group.ProActiveGroup;

import functionalTests.ComponentTest;
import functionalTests.component.Message;


/**
 * This test deploys a distributed component system and makes sure migration is effective by
 * invoking methods on migrated components (through singleton, collection, gathercast and multicast interfaces)
 *
 * @author Matthieu Morel
 */
public class Test extends ComponentTest {

    /**
         *
         */
    private static final long serialVersionUID = 597685496464004752L;
    public static String MESSAGE = "-->m";

    //ComponentsCache componentsCache;
    public Test() {
        super("migration of components", "migration of components");
    }

    /* (non-Javadoc)
     * @see testsuite.test.FunctionalTest#action()
     */
    @org.junit.Test
    public void action() throws Exception {
        DummyAO testAO = (DummyAO) ProActive.newActive(DummyAO.class.getName(),
                new Object[] {  });
        testAO.go();
    }

    private int append(StringBuffer buffer, Message message) {
        int nb_messages = 0;
        if (ProActiveGroup.isGroup(message)) {
            for (int i = 0; i < ProActiveGroup.size(message); i++) {
                nb_messages += append(buffer,
                    (Message) ProActiveGroup.get(message, i));
            }
        } else {
            buffer.append(message.getMessage());
            nb_messages++;
        }
        return nb_messages;
    }
}
