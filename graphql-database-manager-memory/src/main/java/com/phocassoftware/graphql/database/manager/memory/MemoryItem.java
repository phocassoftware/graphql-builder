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
package com.phocassoftware.graphql.database.manager.memory;

import tools.jackson.databind.JsonNode;
import com.phocassoftware.graphql.database.manager.Table;
import com.google.common.collect.HashMultimap;

public class MemoryItem {

	private String organisationId;
	private String id;
	
	public MemoryItem(HashMultimap<String, String> links, Table entity) {
	}

	public static MemoryItem deleted() {
		// TODO Auto-generated method stub
		return null;
	}

	public void deleteLinks() {
		// TODO Auto-generated method stub
		
	}

	public void deleteLinksTo(MemoryItem item) {
	}

	public boolean isDeleted() {
		return false; 
	}

	public <T> T getEntity() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
