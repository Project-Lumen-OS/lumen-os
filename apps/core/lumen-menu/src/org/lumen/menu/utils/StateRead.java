// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.utils;

import org.lumen.menu.MenuState;

/** One device read into a menu state; may throw (see {@link MenuValues#read}). */
public interface StateRead {
    void into(MenuState.Builder b) throws Exception;
}
