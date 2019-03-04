/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
 * %%
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
 * =========================LICENSE_END==================================
 */
package org.dropProject.controllers


import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.Assignee
import org.dropProject.dao.Assignment
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.dropProject.services.GitClient
import java.io.File
import java.util.logging.Logger


@Component
@Transactional
class ApplicationContextListener(val assignmentRepository: AssignmentRepository,
                                      val assigneeRepository: AssigneeRepository,
                                      val gitClient: GitClient) : ApplicationListener<ContextRefreshedEvent> {

    companion object {
        const val sampleJavaAssignmentPrivateKey = "-----BEGIN RSA PRIVATE KEY-----\n" +
                "MIIEogIBAAKCAQEAlw5XmMJOjiMGylXnfWTKVH2sAHs7KuQHUxnlisWI0EHmB2Bf\n" +
                "hp1xt32ulxhhdTdNPMkGMQjsEH79tDl9+c1DO22tCaOq2VA6olB2/vaeBCOaWm8J\n" +
                "u1z4xu6OKASgYMK9SMoLXqFKE1m2gE8QLi3jzH9x1X6Pk2rFSywnpWGtWfqNKlPl\n" +
                "brEhetf6Ztn/1k3jrRoMFCSVw8IPhJJfhSnPwu8PzLvUg5b4u/lVE3Jieq+QiKKi\n" +
                "aFV4jalxqD3QHpJT4mpAbCHRa/tCsCblGEOeQYloI8OW1LpYOpWkwDPzTEseuCF6\n" +
                "fJ/YVg2iiKnsO7OnuvfoCzN4em/NhZWnLqSVKQIDAQABAoIBAFdG+CHREuZZIpFB\n" +
                "tTDkTWsn+XuFuBf+DKVdLx1RKM17ZdcZPdhfm9azDW9LrPO28i+Ozr8CMrMNTLUX\n" +
                "CsyMZq4tnm8VW5+YFWi3KSoDgCVQFNzvjyXsf+kg6I4Crk959TfbVVplwpEPorzb\n" +
                "8bNc3GPJtxHtwDMi27+lUXrixvBXR53V0LdpTGA8BmxTzlnTi/889/bhi7yWSx87\n" +
                "hinB+a0Zo7/FKoSWVLflUbF99/xIk34GPpeDBVnaUkiK6WrTVVK011Om8o0Krfyb\n" +
                "lj2Io+wH91JSYXg7JqbQbwdwfqHboK12JkOTsDUVnsy1lHdc6cmiJzEzSkS5QjSw\n" +
                "4I8gDaECgYEA5owYoZL5tqBxGw2ryXtS4ap21Uj7xJ+eXE2gCkBd94P3kaRC0Xco\n" +
                "KUzbyvemeEU1pUEo3ksv/uQz1TtbGe3MLVLw4XDPuXl7oielqF7BoXA8pzWKiICO\n" +
                "PCaeMjT6UrlI3QBCtRs8QGc+MxnIdr8x0jS3EISqZhmwWjAX+u7Cg90CgYEAp7ua\n" +
                "j1b6JZKkKQ0KSA6uHPCGoqCjV+hf9AaXABbIOY1M+BkGbg7ttoayRvuolkspJNlf\n" +
                "efNolucbBFg+cN5GbUNtyHHtoODRW1XwFGIBS35tmfXFEjSOHr3vs2Sm4R90Fub5\n" +
                "GVYgRaZufOXvCjqAA7I5pJhMm5stRz25/ymb970CgYAdJSbT/j2dTcker2rBLNr8\n" +
                "dk1Rh0l0wO0HJDUQNrTqXn+EpOxhiJvGJNZAYXBlEfLHMmaVO5IUugqncTqCG6LN\n" +
                "NAgJp/ZKr0Xm6PYzQ89ctlCknsslmILircsf87yViqDgd3D3bjr+tU6SrTa/dEo7\n" +
                "Fbjy2KKmB6dYr23Ipjhm7QKBgBJe/9y3QAqhdw1v+jJOOU++IGDrizhzoR7PIfbG\n" +
                "iAOVsFp0Ezo2tF6Lfjc8FQjxDn6UuFpZCJmOkmz1ZVFjZv9MpVeQ8t/t/8ArN3Jk\n" +
                "EZQ9Mq/sNTt7Oh2v2/MgEQ8TLNndTmcyAbLfObbAUGAkbCT7fkjCzZE1e84Tuq1x\n" +
                "1z1ZAoGAJQMeLuX145SFlRR+XCvFp2hAaK2gI/IU81G9LgJu1EMFp6sBiFtw+4ak\n" +
                "bHocX1drNS/7RIB/BeLxwmwBGM8QkO0EOlrEpNCBvLJsTJb7ES2lU27h2RO36mJW\n" +
                "f0gaNCy09YGQejiDUeWBNcPBxn2eQtDvhFpdJlvroO8LWVn2jmg=\n" +
                "-----END RSA PRIVATE KEY-----"

        const val sampleJavaAssignmentPublicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCXDl" +
                "eYwk6OIwbKVed9ZMpUfawAezsq5AdTGeWKxYjQQeYHYF+GnXG3fa6XGGF1N008yQYxCOwQ" +
                "fv20OX35zUM7ba0Jo6rZUDqiUHb+9p4EI5pabwm7XPjG7o4oBKBgwr1IygteoUoTWbaATx" +
                "AuLePMf3HVfo+TasVLLCelYa1Z+o0qU+VusSF61/pm2f/WTeOtGgwUJJXDwg+Ekl+FKc/C" +
                "7w/Mu9SDlvi7+VUTcmJ6r5CIoqJoVXiNqXGoPdAeklPiakBsIdFr+0KwJuUYQ55BiWgjw5" +
                "bUulg6laTAM/NMSx64IXp8n9hWDaKIqew7s6e69+gLM3h6b82FlacupJUp"
    }

    val LOG = Logger.getLogger(this.javaClass.name)

    @Value("\${dropProject.maven.home}")
    val mavenHome : String = ""

    @Value("\${dropProject.maven.repository}")
    val mavenRepository : String = ""

    @Value("\${assignments.rootLocation}")
    val assignmentsRootLocation: String = ""

    override fun onApplicationEvent(event: ContextRefreshedEvent) {

        LOG.info("************ Starting Drop Project **************")
        LOG.info("Maven home: ${mavenHome}")
        LOG.info("Maven repository: ${mavenRepository}")
        LOG.info("Environment variables:")
        for ((key, value) in System.getenv()) {
            LOG.info("\t$key : $value")
        }
        LOG.info("*************************************************")

        // It it's a fresh instance, create an initial assignment just to play
        val assignments = assignmentRepository.findAll()
        if (assignments == null || assignments.size == 0) {

            val assignment = Assignment(id = "sampleJavaProject", name = "Sample Java Assignment",
                    packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
                    submissionMethod = SubmissionMethod.UPLOAD,
                    gitRepositoryUrl = "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    gitRepositoryPrivKey = sampleJavaAssignmentPrivateKey,
                    gitRepositoryPubKey = sampleJavaAssignmentPublicKey,
                    gitRepositoryFolder = "sampleJavaProject",
                    active = true)

            val gitRepository = assignment.gitRepositoryUrl
            try {
                val directory = File(assignmentsRootLocation, assignment.id)
                if (directory.exists()) {
                    directory.deleteRecursively()
                }
                gitClient.clone(gitRepository, directory, assignment.gitRepositoryPrivKey!!.toByteArray())
                LOG.info("[${assignment.id}] Successfuly cloned ${gitRepository} to ${directory}")
                // only save if it successfully cloned the assignment
                assignmentRepository.save(assignment)

                assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))
                assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student2"))

            } catch (e: Exception) {
                LOG.severe("Error cloning ${gitRepository} - ${e}")
            }
        }
    }

}