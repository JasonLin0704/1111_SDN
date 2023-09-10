/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nycu.sdnfv.vrouter;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onlab.packet.Ip4Address;
import java.util.List;
import java.util.function.Function;

public class QuaggaConfig extends Config<ApplicationId> {

	public String information(String name) {
		return get(name, null);
	}

	public List<Ip4Address> getPeers(String name) {
		Function<String, Ip4Address> f = x -> Ip4Address.valueOf(x);
		return getList(name, f);
	}
}