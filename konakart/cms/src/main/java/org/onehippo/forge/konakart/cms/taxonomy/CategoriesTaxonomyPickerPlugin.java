/*
 * =========================================================
 * Hippo CMS - Konakart
 * https://bitbucket.org/jmirc/hippo-cms-konakart
 * =========================================================
 * Copyright 2012
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================================================
 */

package org.onehippo.forge.konakart.cms.taxonomy;

import org.apache.wicket.markup.html.basic.Label;
import org.hippoecm.frontend.plugin.IPluginContext;
import org.hippoecm.frontend.plugin.config.IPluginConfig;
import org.onehippo.taxonomy.api.Taxonomy;
import org.onehippo.taxonomy.plugin.TaxonomyPickerPlugin;

public class CategoriesTaxonomyPickerPlugin extends TaxonomyPickerPlugin {

    public CategoriesTaxonomyPickerPlugin(IPluginContext context, IPluginConfig config) {
        super(context, config);

        // Remove the default title
        super.get("title").replaceWith(new Label("title", config.getString("caption")));

        // Synchronize the categories

    }
}
