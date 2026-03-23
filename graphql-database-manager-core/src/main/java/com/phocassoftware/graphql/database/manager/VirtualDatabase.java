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

import com.phocassoftware.graphql.builder.annotations.Context;
import com.phocassoftware.graphql.database.manager.util.BackupItem;
import com.phocassoftware.graphql.database.manager.util.HistoryBackupItem;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Context
public class VirtualDatabase {

	private final Database database;

	public VirtualDatabase(Database database) {
		this.database = database;
	}

	public <T extends Table> List<T> delete(String organisationId, Class<T> type) {
		return database.delete(organisationId, type).join();
	}

	public <T extends Table> T delete(T entity, boolean deleteLinks) {
		return database.delete(entity, deleteLinks).join();
	}

	public <T extends Table> List<T> getLinks(final Table entry, Class<T> target) {
		return database.getLinks(entry, target).join();
	}

	public Boolean destroyOrganisation(final String organisationId) {
		return database.destroyOrganisation(organisationId).join();
	}

	public <T extends Table> T get(Class<T> type, String id) {
		return database.get(type, id).join();
	}

	public <T extends Table> List<T> get(Class<T> type, List<String> ids) {
		return database.get(type, ids).join();
	}

	public <T extends Table> T getLink(final Table entry, Class<T> target) {
		return database.getLink(entry, target).join();
	}

	public Set<String> getLinkIds(Table entity, Class<? extends Table> type) {
		return database.getLinkIds(entity, type);
	}

	public <T extends Table> Optional<T> getLinkOptional(final Table entry, Class<T> target) {
		return database.getLinkOptional(entry, target).join();
	}

	public <T extends Table> Optional<T> getOptional(Class<T> type, String id) {
		return database.getOptional(type, id).join();
	}

	public String getSourceOrganisationId(Table entity) {
		return database.getSourceOrganisationId(entity);
	}

	public <T extends Table> T link(T entity, Class<? extends Table> class1, String targetId) {
		return database.link(entity, class1, targetId).join();
	}

	public <T extends Table> T links(T entity, Class<? extends Table> class1, List<String> targetIds) {
		return database.links(entity, class1, targetIds).join();
	}

	public String newId() {
		return database.newId();
	}

	public <T extends Table> T put(T entity) {
		return database.put(entity).join();
	}

	public <T extends Table> T put(T entity, boolean check) {
		return database.put(entity, check).join();
	}

	public <T extends Table> T putGlobal(T entity) {
		return database.putGlobal(entity).join();
	}

	public <T extends Table> List<T> query(Class<T> type) {
		return database.query(type).join();
	}

	public <T extends Table> List<T> query(Query<T> query) {
		return database.query(query).join();
	}

	public <T extends Table> List<T> query(Class<T> type, Function<QueryBuilder<T>, QueryBuilder<T>> func) {
		return database.query(type, func).join();
	}

	public <T extends Table> List<T> queryGlobal(Class<T> type, String id) {
		return database.queryGlobal(type, id).join();
	}

	public <T extends Table> T queryGlobalUnique(Class<T> type, String id) {
		return database.queryGlobalUnique(type, id).join();
	}

	public <T extends Table> List<T> queryHistory(QueryHistory<T> query) {
		return database.queryHistory(query).join();
	}

	public <T extends Table> List<T> querySecondary(Class<T> type, String id) {
		return database.querySecondary(type, id).join();
	}

	public <T extends Table> T querySecondaryUnique(Class<T> type, String id) {
		return database.querySecondaryUnique(type, id).join();
	}

	public <T extends Table> Void restoreBackup(List<BackupItem> entities) {
		return database.restoreBackup(entities).join();
	}

	public <T extends Table> Void restoreHistoryBackup(List<HistoryBackupItem> entities) {
		return database.restoreHistoryBackup(entities).join();
	}

	public void setOrganisationId(String organisationId) {
		database.setOrganisationId(organisationId);
	}

	public VirtualDatabase withOrganisationId(String organisationId) {
		return new VirtualDatabase(database.withOrganisationId(organisationId));
	}

	public List<BackupItem> takeBackup(String organisationId) {
		return database.takeBackup(organisationId).join();
	}

	public List<HistoryBackupItem> takeHistoryBackup(String organisationId) {
		return database.takeHistoryBackup(organisationId).join();
	}

	public <T extends Table> T takeHistoryBackup(final T entity, final Class<? extends Table> type, final String targetId) {
		return database.unlink(entity, type, targetId).join();
	}

	public String getOrganisationId() {
		return database.getOrganisationId();
	}
}
