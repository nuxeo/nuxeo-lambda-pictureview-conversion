/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Andrei Nechaev
 */

package org.nuxeo.lambda.image.conversion.work;

import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_FACET;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.runtime.api.Framework;

public class ImagingRecomputeWork extends AbstractWork {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    protected final static int BATCH_SIZE = 10;

    protected String nxqlQuery;

    public ImagingRecomputeWork(String nxqlQuery) {
        this.nxqlQuery = nxqlQuery;
    }

    @Override
    public String getTitle() {
        return "Picture Views Recomputation";
    }

    @Override
    public void work() {
        setProgress(Progress.PROGRESS_INDETERMINATE);

        openSystemSession();
        DocumentModelList docs = session.query(nxqlQuery);
        EventService eventService = Framework.getService(EventService.class);

        setStatus("Generating views");
        for (DocumentModel doc : docs) {
            if (doc.hasFacet(PICTURE_FACET)) {
                EventContext ctx = new DocumentEventContext(session, session.getPrincipal(), doc);
                eventService.fireEvent(ctx.newEvent("rebuildWithLambda"));
            }
        }
        setStatus("Done");
    }

}
