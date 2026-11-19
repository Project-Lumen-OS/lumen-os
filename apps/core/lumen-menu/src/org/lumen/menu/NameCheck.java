// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/** The stock answer to a new user picture mode name (newsettings PictureViewModel, picture_customize). */
public enum NameCheck {
    OK,
    /** toast_custom_name_not_empty */
    EMPTY,
    /** toast_custom_name_repeat */
    TAKEN
}
