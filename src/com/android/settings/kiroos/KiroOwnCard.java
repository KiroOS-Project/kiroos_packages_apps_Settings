/*
 * Copyright (C) 2026 KiroOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.kiroos;

/**
 * A preference whose layout draws its own card (the About phone header): KiroGroupDecoration
 * neither insets it nor draws a group card behind it, so it keeps the page's 16dp margins and
 * the card that follows it starts a new, separate card of the same width.
 */
public interface KiroOwnCard {
}
