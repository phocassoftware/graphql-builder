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
package com.phocassoftware.graphql.database.manager.util;

import com.phocassoftware.graphql.database.manager.Table;
import com.phocassoftware.graphql.database.manager.annotations.History;

public final class HistoryCoreUtil {

	public static boolean hasHistory(Class<? extends Table> type) {
		Class<?> tmp = type;
		return tmp.getDeclaredAnnotation(History.class) != null;
	}

	public static boolean hasHistory(Table type) {
		return hasHistory(type.getClass());
	}
}
