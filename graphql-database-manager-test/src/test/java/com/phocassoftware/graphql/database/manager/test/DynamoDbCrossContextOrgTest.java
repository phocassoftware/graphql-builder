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
package com.phocassoftware.graphql.database.manager.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.*;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.phocassoftware.graphql.builder.SchemaBuilder;
import com.phocassoftware.graphql.database.manager.VirtualDatabase;
import com.phocassoftware.graphql.database.manager.test.crossorg.*;

import graphql.*;

public class DynamoDbCrossContextOrgTest {

	@TestDatabase
	void testDirect(VirtualDatabase db) {
		var global = db.withOrganisationId("global2");
		var globalEntry = global.put(new GlobalEntry("top"));

		var firstDb = db.withOrganisationId("firstOrg2");
		firstDb.put(new OrgEntry("first", globalEntry.getId()));

		var secondDb = db.withOrganisationId("secondOrg2");
		secondDb.put(new OrgEntry("second", globalEntry.getId()));

		firstDb.put(new OrgRelated("related1"));
		secondDb.put(new OrgRelated("related2"));

		var orgs = new ArrayList<>(globalEntry.getOrgs(global));

		Assertions.assertEquals(2, orgs.size());

		orgs.sort((a, b) -> a.getData().getName().compareTo(b.getData().getName()));

		var org1 = orgs.get(0);
		var org2 = orgs.get(1);

		assertEquals("first", org1.getData().getName());
		assertEquals("second", org2.getData().getName());

		var related1 = org1.getData().getRelated((VirtualDatabase) org1.getLocalContext());
		var related2 = org2.getData().getRelated((VirtualDatabase) org2.getLocalContext());

		Assertions.assertEquals(1, related1.size());
		Assertions.assertEquals(1, related2.size());

		assertEquals("related1", related1.get(0).getName());
		assertEquals("related2", related2.get(0).getName());
	}

	@TestDatabase
	void testViaGraphql(VirtualDatabase db) throws JsonMappingException, JsonProcessingException {
		var global = db.withOrganisationId("global3");
		var globalEntry = global.put(new GlobalEntry("topGraph"));

		var firstDb = db.withOrganisationId("firstOrg");
		var first = new OrgEntry("first", globalEntry.getId());
		first.setId("1");
		firstDb.put(first);

		var second = new OrgEntry("second", globalEntry.getId());
		var secondDb = db.withOrganisationId("secondOrg");
		second.setId("2");
		secondDb.put(second);

		firstDb.put(new OrgRelated("related1Graph"));
		secondDb.put(new OrgRelated("related2Graph"));

		var data = execute("""
			query {
				relatedGlobalEntries {
					name
					orgs {
						name
						related {
							name
						}
					}
				}
			}
			""", c -> c.context(global)).getData();

		var mapper = new ObjectMapperCreator().get();

		var json = mapper.valueToTree(data);

		var expected = mapper.readTree("""
			{
			  "relatedGlobalEntries": [
			    {
			      "name": "topGraph",
			      "orgs": [
			        {
			          "name": "first",
			          "related": [
			            {
			              "name": "related1Graph"
			            }
			          ]
			        },
			        {
			          "name": "second",
			          "related": [
			            {
			              "name": "related2Graph"
			            }
			          ]
			        }
			      ]
			    }
			  ]
			}
			""");
		assertEquals(
			expected,
			json
		);

	}

	private ExecutionResult execute(String query, Consumer<ExecutionInput.Builder> modify) {
		GraphQL schema = GraphQL
			.newGraphQL(
				SchemaBuilder.builder().classpath("com.phocassoftware.graphql.database.manager.test.crossorg").scalar(InstantCoercing.INSTANCE).build().build()
			)
			.build();
		var input = ExecutionInput.newExecutionInput();
		input.query(query);
		modify.accept(input);
		ExecutionResult result = schema.execute(input);
		return result;
	}

}
