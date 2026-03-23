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
package com.phocassoftware.graphql.database.manager.test.crossorg;

import java.util.List;

import com.phocassoftware.graphql.builder.annotations.Query;
import com.phocassoftware.graphql.database.manager.*;

import graphql.execution.DataFetcherResult;

public class GlobalEntry extends Table {

	private String name;

	public GlobalEntry(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public List<DataFetcherResult<OrgEntry>> getOrgs(VirtualDatabase db) {
		return db
			.queryGlobal(OrgEntry.class, this.getId())
			.stream()
			.map(
				org -> DataFetcherResult
					.<OrgEntry>newResult()
					.data(org)
					.localContext(db.withOrganisationId(TableAccess.getTableSourceOrganisation(org)))
					.build()
			)
			.toList();
	}

	@Query
	public static List<GlobalEntry> relatedGlobalEntries(VirtualDatabase db) {
		return db.query(GlobalEntry.class);
	}
}
