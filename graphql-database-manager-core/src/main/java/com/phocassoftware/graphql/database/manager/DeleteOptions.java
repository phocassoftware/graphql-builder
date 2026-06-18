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

import com.phocassoftware.graphql.database.manager.util.TableCoreUtil;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record DeleteOptions(DeleteMode mode, Set<Class<? extends Table>> includedTypes) {

	public DeleteOptions {
		Objects.requireNonNull(mode, "mode");
		includedTypes = includedTypes == null
			? Set.of()
			: includedTypes.stream().map(DeleteOptions::normalizeType).collect(Collectors.toUnmodifiableSet());
	}

	public static DeleteOptions rejectIfLinked() {
		return new DeleteOptions(DeleteMode.REJECT_IF_LINKED, Set.of());
	}

	public static DeleteOptions unlinkOnly() {
		return new DeleteOptions(DeleteMode.UNLINK_ONLY, Set.of());
	}

	public static DeleteOptions cascade() {
		return new DeleteOptions(DeleteMode.CASCADE, Set.of());
	}

	public static DeleteOptions cascade(Set<Class<? extends Table>> includedTypes) {
		return new DeleteOptions(DeleteMode.CASCADE, includedTypes);
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Table> normalizeType(Class<? extends Table> type) {
		return TableCoreUtil.baseClass((Class<Table>) Objects.requireNonNull(type, "includedTypes entry"));
	}
}
