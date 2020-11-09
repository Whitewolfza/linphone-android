/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package za.co.zencomms.chat;

import java.util.ArrayList;
import org.linphone.core.EventLog;
import za.co.zencomms.contacts.LinphoneContact;

interface ChatMessagesGenericAdapter {
    void addToHistory(EventLog log);

    void addAllToHistory(ArrayList<EventLog> logs);

    void setContacts(ArrayList<LinphoneContact> participants);

    void refresh(EventLog[] history);

    void clear();

    Object getItem(int i);

    void removeItem(int i);

    boolean removeFromHistory(EventLog eventLog);
}
