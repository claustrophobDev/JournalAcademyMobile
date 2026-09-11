package com.claustrophob.journal.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// вход

@Serializable
data class CityDto(
    @SerialName("id_city") val id: Int = 0,
    val name: String = "",
    val prefix: String = "",
    @SerialName("timezone_name") val timezone: String = "",
    @SerialName("country_code") val countryCode: String = "",
)

@Serializable
data class LoginRequest(
    @SerialName("application_key") val applicationKey: String,
    @SerialName("id_city") val idCity: Int,
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in_access") val expiresInAccess: Long = 0,
    @SerialName("expires_in_refresh") val expiresInRefresh: Long = 0,
    @SerialName("user_role") val userRole: String = "",
    @SerialName("user_type") val userType: Int = 0,
)

@Serializable
data class FieldErrorDto(val field: String = "", val message: String = "")

// профиль студента

@Serializable
data class VisibilityDto(
    @SerialName("is_debtor") val isDebtor: Boolean = false,
    @SerialName("is_school") val isSchool: Boolean = false,
    @SerialName("is_video_courses") val isVideoCourses: Boolean = false,
)

@Serializable
data class UserInfoDto(
    @SerialName("student_id") val studentId: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("current_group_id") val currentGroupId: Int = 0,
    @SerialName("stream_id") val streamId: Int = 0,
    @SerialName("stream_name") val streamName: String = "",
    @SerialName("photo") val photo: String = "",
    @SerialName("achieves_count") val achievesCount: Int = 0,
    val level: Int = 0,
    val age: Int = 0,
    val birthday: String = "",
    @SerialName("last_date_visit") val lastDateVisit: String = "",
    val gender: Int = 0,
    @SerialName("registration_date") val registrationDate: String = "",
    @SerialName("study_form_short_name") val studyForm: String = "",
    val visibility: VisibilityDto = VisibilityDto(),
)

@Serializable
data class AchievementDto(
    val id: Int = 0,
    @SerialName("translate_key") val translateKey: String = "",
    @SerialName("is_active") val isActive: Boolean = false,
)

@Serializable
data class GroupDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("group_status") val groupStatus: Int = 0,
    @SerialName("is_primary") val isPrimary: Boolean = false,
)

// расписание

@Serializable
data class LessonDto(
    val date: String = "",
    val lesson: Int = 0,
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("finished_at") val finishedAt: String = "",
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("subject_name") val subjectName: String = "",
    @SerialName("room_name") val roomName: String = "",
)

@Serializable
data class EventDto(
    val title: String = "",
    val status: Int = 0,
    @SerialName("event_text") val eventText: String = "",
    val location: String = "",
    val type: Int = 0,
    val date: String = "",
    val time: String = "",
)

// оценки и посещения

@Serializable
data class VisitDto(
    @SerialName("date_visit") val dateVisit: String = "",
    @SerialName("lesson_number") val lessonNumber: Int = 0,
    @SerialName("status_was") val statusWas: Int = 0,
    @SerialName("spec_id") val specId: Int = 0,
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("spec_name") val specName: String = "",
    @SerialName("lesson_theme") val lessonTheme: String = "",
    @SerialName("control_work_mark") val controlWorkMark: Int = 0,
    @SerialName("home_work_mark") val homeWorkMark: Int = 0,
    @SerialName("lab_work_mark") val labWorkMark: Int = 0,
    @SerialName("class_work_mark") val classWorkMark: Int = 0,
    @SerialName("practical_work_mark") val practicalWorkMark: Int = 0,
    @SerialName("final_work_mark") val finalWorkMark: Int = 0,
)

@Serializable
data class ExamResultDto(
    val teacher: String = "",
    val spec: String = "",
    val mark: Int = 0,
    @SerialName("mark_type") val markType: Int = 0,
    val date: String = "",
    @SerialName("exam_id") val examId: Int = 0,
    @SerialName("comment_teach") val commentTeach: String = "",
)

// домашка

@Serializable
data class HomeworkStudDto(
    val id: Int = 0,
    val filename: String = "",
    @SerialName("stud_answer") val answerText: String = "",
    @SerialName("file_path") val filePath: String = "",
    val mark: Int = 0,
    @SerialName("auto_mark") val autoMark: Boolean = false,
    @SerialName("creation_time") val creationTime: String = "",
)

@Serializable
data class HomeworkCommentDto(
    @SerialName("evaluation_id") val id: Int = 0,
    @SerialName("evaluation_comment") val message: String = "",
    @SerialName("text_comment") val textComment: String = "",
    @SerialName("attachment_path") val attachmentPath: String = "",
    @SerialName("date_updated") val dateUpdated: String = "",
)

@Serializable
data class HomeworkDto(
    val id: Int = 0,
    @SerialName("id_spec") val idSpec: Int = 0,
    @SerialName("fio_teach") val teacher: String = "",
    val theme: String = "",
    @SerialName("creation_time") val creationTime: String = "",
    @SerialName("completion_time") val completionTime: String = "",
    @SerialName("overdue_time") val overdueTime: String = "",
    val filename: String = "",
    @SerialName("file_path") val filePath: String = "",
    val comment: String = "",
    @SerialName("name_spec") val nameSpec: String = "",
    val status: Int = 0,
    @SerialName("common_status") val commonStatus: Int = 0,
    @SerialName("homework_stud") val studentWork: HomeworkStudDto? = null,
    @SerialName("homework_comment") val teacherComment: HomeworkCommentDto? = null,
    @SerialName("cover_image") val coverImage: String = "",
)

@Serializable
data class PageCounterDto(
    @SerialName("counter_type") val counterType: Int = 0,
    val counter: Int = 0,
)

// новости

@Serializable
data class NewsDto(
    @SerialName("id_bbs") val id: Int = 0,
    val theme: String = "",
    val time: String = "",
    val viewed: Boolean = false,
)

@Serializable
data class NewsDetailDto(
    @SerialName("id_bbs") val id: Int = 0,
    val theme: String = "",
    @SerialName("text_bbs") val text: String = "",
    @SerialName("is_viewed") val isViewed: Boolean = false,
)

// то, что показываем на главной

@Serializable
data class ChartPointDto(
    val date: String = "",
    val points: Double = 0.0,
    @SerialName("previous_points") val previousPoints: Double = 0.0,
)

@Serializable
data class AttendanceStatDto(
    val diffMonth: Double = 0.0,
    val diffWeek: Double = 0.0,
    val statMonth: Double = 0.0,
    val statWeek: Double = 0.0,
    val statTotal: Double = 0.0,
)

@Serializable
data class AcademicProgressDto(
    val classwork: Double = 0.0,
    val control: Double = 0.0,
    val coursework: Double = 0.0,
    val exams: Double = 0.0,
    val homework: Double = 0.0,
    val laboratory: Double = 0.0,
    val testing: Double = 0.0,
    val total: Double = 0.0,
)

@Serializable
data class AcademicPerformanceDto(
    val diffMonth: Double = 0.0,
    val totalMonth: Double = 0.0,
    val totalAllTime: Double = 0.0,
    val maxAllowedPoint: Double = 0.0,
    val progress: AcademicProgressDto = AcademicProgressDto(),
)

@Serializable
data class LeaderPositionDto(
    val totalCount: Int = 0,
    val studentPosition: Int = 0,
    val weekDiff: Int = 0,
    val monthDiff: Int = 0,
)

@Serializable
data class LeaderRowDto(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("photo_path") val photoPath: String = "",
    val position: Int = 0,
    val amount: Double = 0.0,
)

@Serializable
data class FutureExamDto(
    val spec: String = "",
    val date: String = "",
)

@Serializable
data class ActivityDto(
    val date: String = "",
    val action: Int = 0,
    @SerialName("current_point") val currentPoint: Double = 0.0,
    @SerialName("point_types_id") val pointTypeId: Int = 0,
    @SerialName("point_types_name") val pointTypeName: String = "",
    @SerialName("achievements_id") val achieveId: Int = 0,
    @SerialName("achievements_name") val achieveName: String = "",
    @SerialName("achievements_type") val achieveType: Int = 0,
    val badge: Int = 0,
)

// библиотека

@Serializable
data class LibraryMaterialDto(
    @SerialName("material_id") val id: Int = 0,
    val theme: String = "",
    @SerialName("material_type") val materialType: Int = 0,
    @SerialName("id_spec") val specId: Int = 0,
    @SerialName("name_spec") val specName: String = "",
    val date: String = "",
    @SerialName("sort_date") val sortDate: Long = 0,
    @SerialName("current_week") val currentWeek: Int = 0,
    @SerialName("public_week") val publicWeek: Int = 0,
    val description: String = "",
    val url: String = "",
    val link: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    val filename: String = "",
    @SerialName("cover_image") val coverImage: String = "",
)

@Serializable
data class LibraryCountDto(
    @SerialName("material_type_id") val materialType: Int = 0,
    @SerialName("materials_count") val materialsCount: Int = 0,
    @SerialName("new_count") val newCount: Int = 0,
    @SerialName("recommended_count") val recommendedCount: Int = 0,
)

// оплата

@Serializable
data class NewPaymentDto(
    val id: Int = 0,
    @SerialName("amount_debt") val amountDebt: Double = 0.0,
    @SerialName("amount_next") val amountNext: Double = 0.0,
    @SerialName("amount_to_pay") val amountToPay: Double = 0.0,
    @SerialName("pay_date_start") val payDateStart: String = "",
    @SerialName("purpose_of_payment") val purposeOfPayment: String = "",
    @SerialName("organization_name") val organizationName: String = "",
    @SerialName("settlement_account") val settlementAccount: String = "",
    @SerialName("bank_name") val bankName: String = "",
    @SerialName("payer_full_name") val payerFullName: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class LegacyPaymentDto(
    @SerialName("message_type") val messageType: Int = 0,
    @SerialName("date_start") val dateStart: String = "",
    @SerialName("pay_date_start") val payDateStart: String = "",
    val amount: Double = 0.0,
)

@Serializable
data class PaymentRequisitesDto(
    val okpo: String = "",
    val amount: Double = 0.0,
    val mfo: String = "",
    @SerialName("settlement_account") val settlementAccount: String = "",
    @SerialName("law_person") val lawPerson: String = "",
)

@Serializable
data class PaymentInfoDto(
    @SerialName("full_name") val fullName: String = "",
    @SerialName("student_cost") val studentCost: Double = 0.0,
    @SerialName("account_amount") val accountAmount: String = "",
    @SerialName("online_link") val onlineLink: String = "",
    @SerialName("online_link_has") val onlineLinkHas: Boolean = false,
    @SerialName("one_c_code") val contractCode: String = "",
    @SerialName("contract_offer") val contractOffer: String = "",
    val payment: NewPaymentDto = NewPaymentDto(),
    @SerialName("legacy_payment") val legacyPayment: LegacyPaymentDto = LegacyPaymentDto(),
    val requisites: PaymentRequisitesDto = PaymentRequisitesDto(),
)

@Serializable
data class PaymentPlanDto(
    val id: Int = 0,
    val description: String = "",
    val price: Double = 0.0,
    @SerialName("payment_date") val paymentDate: String = "",
    val status: Int = 0,
)

@Serializable
data class PaymentHistoryDto(
    val description: String = "",
    val amount: Double = 0.0,
    val date: String = "",
)

// портфолио

@Serializable
data class PortfolioWorkDto(
    val id: Int = 0,
    @SerialName("portfolio_title") val title: String = "",
    @SerialName("portfolio_description") val description: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("subject_name") val subjectName: String = "",
    val mark: Int = 0,
    val url: String = "",
)

@Serializable
data class PortfolioRequestDto(
    val id: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("teacher_name") val teacherName: String = "",
    @SerialName("subject_name") val subjectName: String = "",
    @SerialName("student_comment") val studentComment: String = "",
    @SerialName("teacher_comment") val teacherComment: String = "",
    @SerialName("approve_status") val approveStatus: Int = 0,
)
