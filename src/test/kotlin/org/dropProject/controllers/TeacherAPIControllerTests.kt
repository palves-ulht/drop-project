/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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

import org.dropProject.TestsHelper
import org.dropProject.dao.*
import org.dropProject.extensions.getContent
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.dropProject.services.SubmissionService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@Transactional
class TeacherAPIControllerTests: APIControllerTests {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var projectGroupRepository: ProjectGroupRepository

    @Autowired
    lateinit var submissionReportRepository: SubmissionReportRepository

    @Autowired
    lateinit var buildReportRepository: BuildReportRepository

    @Autowired
    lateinit var jUnitReportRepository: JUnitReportRepository

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var testsHelper: TestsHelper

    @Autowired
    lateinit var submissionService: SubmissionService

    fun uploadStudentSubmission(submissionId: String, author: Author, submissionDate: String, submissionName: String,
                                        teacherTestsIndicator: String, teacherTestsProgress: Int,
                                        teacherTestsGoal: Int) : Long {

        val submission = Submission(submissionId = submissionId,
            submissionDate = Timestamp.valueOf(LocalDateTime.parse(submissionDate)),
            status = SubmissionStatus.VALIDATED.code,
            statusDate = Timestamp.valueOf(LocalDateTime.parse(submissionDate)),
            assignmentId = "testJavaProj",
            assignmentGitHash = null,
            submitterUserId = author.userId, submissionMode = SubmissionMode.UPLOAD)

        submissionService.saveSubmissionAndUpdateAssignmentMetrics(submission)

        val groups = projectGroupRepository.getGroupsForAuthor(author.userId)
        lateinit var group : ProjectGroup
        if (groups.isEmpty()) {
            group = ProjectGroup()
            group.authors.add(author)
        } else {
            group = groups[0]
        }
        group.submissions.add(submission)
        projectGroupRepository.save(group)

        author.group = group
        submission.group = group

        authorRepository.save(author)
        submissionRepository.save(submission)

        submissionReportRepository.save(
            SubmissionReport(submissionId = submission.id,
            reportKey = Indicator.PROJECT_STRUCTURE.code, reportValue = "OK")
        )
        submissionReportRepository.save(
            SubmissionReport(submissionId = submission.id,
            reportKey = Indicator.COMPILATION.code, reportValue = "OK")
        )
        submissionReportRepository.save(
            SubmissionReport(submissionId = submission.id,
            reportKey = Indicator.CHECKSTYLE.code, reportValue = "OK")
        )
        submissionReportRepository.save(
            SubmissionReport(submissionId = submission.id,
            reportKey = Indicator.TEACHER_UNIT_TESTS.code, reportValue = teacherTestsIndicator,
            reportProgress = teacherTestsProgress, reportGoal = teacherTestsGoal)
        )

        // this file must be coherent with the report
        val buildReport = BuildReport(buildReport =
        (resourceLoader.getResource("classpath:/initialData/${submissionName}MavenOutput.txt") as ClassPathResource).getContent())
        buildReportRepository.save(buildReport)

        submission.buildReport = buildReport
        submissionRepository.save(submission)

        jUnitReportRepository.save(JUnitReport(submissionId = submission.id,
            fileName = "TEST-org.dropProject.samples.sampleJavaAssignment.TestTeacherProject.xml",
            xmlReport = (resourceLoader.getResource("classpath:/initialData/${submissionName}JUnitXml.txt")as ClassPathResource).getContent()))

        return submission.id
    }

    @Before
    fun setup() {

        assigneeRepository.deleteAll()
        assignmentRepository.deleteAll()
        authorRepository.deleteAll()

        // create initial assignment
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummy",
            gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment01)
        assigneeRepository.save(Assignee(assignmentId = "testJavaProj", authorUserId = "student1"))

        val author = Author(name = "Student 1", userId = "student1")

        authorRepository.save(author)

        uploadStudentSubmission("1", author, "2019-01-01T10:34:00", "javaSubmissionError", "NOK", 1, 2)
        uploadStudentSubmission("2", author, "2019-01-02T11:05:03", "javaSubmissionOk", "OK", 2, 2)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments without authentication`() {
        this.mvc.perform(
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with invalid token`() {
        this.mvc.perform(
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", "invalid")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with a student profile`() {
        val token = generateToken("student1", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("student1", token)))
            .andExpect(status().isForbidden)
    }

    @Test
    @DirtiesContext
    fun `try to get current assignments with a teacher profile`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/current")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
                   {"id":"testJavaProj",
                    "name":"Test Project (for automatic tests)",
                    "packageName":"org.dropProject.sampleAssignments.testProj",
                    "dueDate":null,
                    "submissionMethod":"UPLOAD",
                    "language":"JAVA",
                    "gitRepositoryUrl":"git://dummy",
                    "ownerUserId":"teacher1",
                    "active":true,
                    "numSubmissions":2,
                    "tagsStr":[],
                    "instructions":null }
                ]
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to get an assignment's latest submissions with a teacher profile`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/testJavaProj/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
                  {"projectGroup": {"id": 1,"authors": [{"id": 1,"name": "Student 1"}]},
                   "lastSubmission": {"id": 2,
                                      "submissionDate": "2019-01-02T11:05:03.000+00:00",
                                      "status": "VALIDATED",
                                      "statusDate": "2019-01-02T11:05:03.000+00:00",
                                      "markedAsFinal": false,
                                      "teacherTests": {"numTests": 4,
                                                       "numFailures": 0,
                                                       "numErrors": 0,
                                                       "numSkipped": 0,
                                                       "ellapsed": 0.007,
                                                       "numMandatoryOK": 0,
                                                       "numMandatoryNOK": 0 },
                                      "group": {"id": 1,
                                                "authors": [{"id": 1,"name": "Student 1" }]} },
                    "numSubmissions": 2 }
                ]
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to get a group's submissions to an assignment with a teacher profile`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/testJavaProj/submissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk)
            .andExpect(content().json("""
                [
                   {"id":2,
                    "submissionDate":"2019-01-02T11:05:03.000+00:00",
                    "status":"VALIDATED",
                    "statusDate":"2019-01-02T11:05:03.000+00:00",
                    "markedAsFinal":false,
                    "teacherTests":{"numTests":4,"numFailures":0,"numErrors":0,"numSkipped":0,"ellapsed":0.007,"numMandatoryOK":0,"numMandatoryNOK":0},
                    "overdue":false },
                   {"id":1,
                    "submissionDate":"2019-01-01T10:34:00.000+00:00",
                    "status":"VALIDATED",
                    "statusDate":"2019-01-01T10:34:00.000+00:00",
                    "markedAsFinal":false,
                    "teacherTests":{"numTests":4,"numFailures":1,"numErrors":0,"numSkipped":0,"ellapsed":0.012,"numMandatoryOK":0,"numMandatoryNOK":0},
                    "overdue":false }
                ]
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to get a submission's build report`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/submissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().json("""
                {"numSubmissions":2,
                 "assignment":{"id":"testJavaProj",
                               "name":"Test Project (for automatic tests)",
                               "packageName":"org.dropProject.sampleAssignments.testProj",
                               "dueDate":null,
                               "submissionMethod":"UPLOAD",
                               "language":"JAVA",
                               "gitRepositoryUrl":"git://dummy",
                               "ownerUserId":"teacher1",
                               "active":true,
                               "numSubmissions":2,
                               "instructions":null },
                 "submission":{"id":1,
                               "submissionDate":"2019-01-01T10:34:00.000+00:00",
                               "status":"VALIDATED",
                               "statusDate":"2019-01-01T10:34:00.000+00:00",
                               "markedAsFinal":false },
                 "summary":[{"reportKey":"PS",
                             "reportValue":"OK",
                             "reportProgress":null,
                             "reportGoal":null },
                            {"reportKey":"C",
                             "reportValue":"OK",
                             "reportProgress":null,
                             "reportGoal":null },
                            {"reportKey":"CS",
                             "reportValue":"OK",
                             "reportProgress":null,
                             "reportGoal":null },
                            {"reportKey":"TT",
                             "reportValue":"NOK",
                             "reportProgress":1,
                             "reportGoal":2 }],
                 "buildReport":{"junitSummaryTeacher":"Tests run: 4, Failures: 1, Errors: 0, Time elapsed: 0.012 sec",
                                "junitErrorsTeacher":"FAILURE: org.dropProject.samples.sampleJavaAssignment.TestTeacherProject.testFindMax\njava.lang.AssertionError: expected:<7> but was:<-2147483648>\n\n" }}
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to download a non-existing submission zip file`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/download/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().is5xxServerError())
    }

    @Test
    @DirtiesContext
    fun `try to search for an existing student`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/studentSearch/student1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().json("""
                [
                  {
                    "value": "student1",
                    "text": "Student 1"
                  }
                ]
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to search for a non existing student`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/studentSearch/student2")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().json("""
                []
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to search for an assignment`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignmentSearch/testJavaProj")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().json("""
                [
                  {
                    "value": "testJavaProj",
                    "text": "Test Project (for automatic tests)"
                  }
                ]
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to search for a non existing assignment`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignmentSearch/testProj")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().json("""
                []
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to access a student's history`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/studentHistory/student1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                    {
                        "history": [
                            {
                                "assignment": {
                                    "id": "testJavaProj",
                                    "name": "Test Project (for automatic tests)",
                                    "packageName": "org.dropProject.sampleAssignments.testProj",
                                    "dueDate": null,
                                    "submissionMethod": "UPLOAD",
                                    "language": "JAVA",
                                    "gitRepositoryUrl": "git://dummy",
                                    "ownerUserId": "teacher1",
                                    "active": true,
                                    "numSubmissions": 2,
                                    "instructions": null
                                },
                                "sortedSubmissions": [
                                    {
                                        "id": 2,
                                        "submissionDate": "2019-01-02T11:05:03.000+00:00",
                                        "status": "VALIDATED",
                                        "statusDate": "2019-01-02T11:05:03.000+00:00",
                                        "markedAsFinal": false,
                                        "teacherTests": {
                                            "numTests": 4,
                                            "numFailures": 0,
                                            "numErrors": 0,
                                            "numSkipped": 0,
                                            "ellapsed": 0.007,
                                            "numMandatoryOK": 0,
                                            "numMandatoryNOK": 0
                                        },
                                        "overdue": false,
                                        "group": {
                                            "id": 1,
                                            "authors": [
                                                {
                                                    "id": 1,
                                                    "name": "Student 1"
                                                }
                                            ]
                                        }
                                    },
                                    {
                                        "id": 1,
                                        "submissionDate": "2019-01-01T10:34:00.000+00:00",
                                        "status": "VALIDATED",
                                        "statusDate": "2019-01-01T10:34:00.000+00:00",
                                        "markedAsFinal": false,
                                        "teacherTests": {
                                            "numTests": 4,
                                            "numFailures": 1,
                                            "numErrors": 0,
                                            "numSkipped": 0,
                                            "ellapsed": 0.012,
                                            "numMandatoryOK": 0,
                                            "numMandatoryNOK": 0
                                        },
                                        "overdue": false,
                                        "group": {
                                            "id": 1,
                                            "authors": [
                                                {
                                                    "id": 1,
                                                    "name": "Student 1"
                                                }
                                            ]
                                        }
                                    }
                                ]
                            }
                        ]
                    }
            """.trimIndent()))
    }

    @Test
    @DirtiesContext
    fun `try to access a non existent student's history`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/studentHistory/student2")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().is5xxServerError())
    }

    @Test
    @DirtiesContext
    fun `try to mark a submission as final`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/submissions/1/markAsFinal")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().string("true"))
    }

    @Test
    @DirtiesContext
    fun `try to mark a non existing submission as final`() {
        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/submissions/0/markAsFinal")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().string("false"))
    }

    @Test
    @DirtiesContext
    fun `try to mark the best submissions as final`() {
        assigneeRepository.deleteAll()
        assignmentRepository.deleteAll()
        authorRepository.deleteAll()

        setup()

        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/testJavaProj/previewMarkBestSubmissions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().json("""
                [
                  {"id":2,
                   "submissionDate":"2019-01-02T11:05:03.000+00:00",
                   "status":"VALIDATED",
                   "statusDate":"2019-01-02T11:05:03.000+00:00",
                   "markedAsFinal":false,
                   "teacherTests":{"numTests":4,
                                   "numFailures":0,
                                   "numErrors":0,
                                   "numSkipped":0,
                                   "ellapsed":0.007,
                                   "numMandatoryOK":0,
                                   "numMandatoryNOK":0 },
                   "overdue":false,
                   "group":{"id":1,
                            "authors":[{"id":1,
                                        "name":"Student 1"}]}}]
            """.trimIndent()))

        this.mvc.perform(
            post("/api/teacher/markMultipleAsFinal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[1, 2]")
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk)

        val submissions = submissionRepository.findByStatusOrderByStatusDate(SubmissionStatus.VALIDATED.code)
            .groupBy { it.id }
        assertFalse { submissions[1]?.first()?.markedAsFinal == true } // first submission gets unmarked since 1 and 2 are made by the same student
        assertTrue { submissions[2]?.first()?.markedAsFinal == true }
    }

    @Test
    @DirtiesContext
    fun `try to deactivate an assignment`() {
        assigneeRepository.deleteAll()
        assignmentRepository.deleteAll()
        authorRepository.deleteAll()

        setup()

        val token = generateToken("teacher1", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")), mvc)

        this.mvc.perform(
            get("/api/teacher/assignments/testJavaProj/toggleState")
                .contentType(MediaType.APPLICATION_JSON)
                .header("authorization", testsHelper.header("teacher1", token)))
            .andExpect(status().isOk()).andExpect(content().string("true"))

        assertFalse { assignmentRepository.getReferenceById("testJavaProj").active }
    }
}
