/*******************************************************************************
 * Copyright (c) 2012 GigaSpaces Technologies Ltd. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
import org.hyperic.sigar.OperatingSystem
import org.cloudifysource.utilitydomain.context.ServiceContextFactory

context = ServiceContextFactory.getServiceContext()
config = new ConfigSlurper().parse(new File("${context.serviceDirectory}/scripts/apacheLB-service.properties").toURL())


def node = args[0]
def instanceID= args[1]

def proxyBalancerFullPath = context.attributes.thisInstance["proxyBalancerPath"]

println "removeNode: About to remove ${node} instance (${instanceID}) to httpd-proxy-balancer.conf..."
def proxyConfigFile = new File("${proxyBalancerFullPath}")

def routeStr=""
if ( "${config.useStickysession}" == "true" ) {
    routeStr=" route=" + instanceID
}

def balancerMemberText="BalancerMember " + node + routeStr

def configText = proxyConfigFile.text
def modifiedConfig = configText.replace("${balancerMemberText}", "")
proxyConfigFile.text = modifiedConfig
println "removeNode: Removed ${node} from httpd-proxy-balancer.conf text is now : ${modifiedConfig}..."

def balancerMembers=context.attributes.thisService["balancerMembers"]
if ( balancerMembers != null ) {
    balancerMembers=balancerMembers.replace(",${balancerMemberText},","")
    if ( balancerMembers == "" ) {
        balancerMembers = null
    }
}


context.attributes.thisService["balancerMembers"]=balancerMembers
println "removeNode: Cleaned ${node} from context balancerMembers"

def startScript
startScript="${context.serviceDirectory}/scripts/run.sh"
def stopScript
stopScript="${context.serviceDirectory}/scripts/stop.sh"

builder = new AntBuilder()
builder.sequential {
    exec(executable:"${stopScript}", osfamily:"unix")
    exec(executable:"${startScript}", osfamily:"unix",failonerror: "true")
}
