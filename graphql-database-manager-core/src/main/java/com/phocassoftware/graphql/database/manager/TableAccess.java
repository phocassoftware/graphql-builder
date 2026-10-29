/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.phocassoftware.graphql.database.manager;

import java.util.Map;
import java.util.Set;

public interface TableAccess {
	public static <T extends Table> void setTableSource(
		final T table,
		final String sourceTable,
		final Map<String, Set<String>> links,
		final String sourceOrganisationId
	) {
		table.setSource(sourceTable, links, sourceOrganisationId);
	}

	public static <T extends Table> String getTableSourceOrganisation(final T table) {
		return table.getSourceOrganisationId();
	}

	public static <T extends Table> Map<String, Set<String>> getTableLinks(final T table) {
		return table.getLinks();
	}
}
