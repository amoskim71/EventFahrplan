package nerd.tuxmobil.fahrplan.congress.schedule

import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import info.metadude.android.eventfahrplan.commons.temporal.Moment
import nerd.tuxmobil.fahrplan.congress.NoLogging
import nerd.tuxmobil.fahrplan.congress.models.Lecture
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LayoutCalculatorTest {
    private val conferenceDate = "2020-03-30"
    private var lectureId = 0
    private val layoutCalculator = LayoutCalculator(standardHeight = 1, logging = NoLogging)

    private fun createLecture(date: String? = null, startTime: Int = 0, duration: Int = 0): Lecture {
        val lecture = Lecture((lectureId++).toString())

        if (date != null) {
            val dateUTC = Moment(date)
            dateUTC.plusMinutes(startTime.toLong())
            lecture.dateUTC = dateUTC.toMilliseconds()
        } else {
            lecture.relStartTime = startTime
        }

        return lecture.apply { this.duration = duration }
    }

    @Test
    fun `calculateLayoutParams for empty list returns empty params`() {
        val lectures = listOf<Lecture>()
        val conference = Conference(firstEventStartsAt = 0, lastEventEndsAt = 0)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)

        assertThat(layoutParams).isEmpty()
    }

    @Test
    fun `calculateLayoutParams for single lecture returns margins 0`() {
        val lectures = listOf(createLecture())
        val conference = Conference(firstEventStartsAt = 0, lastEventEndsAt = 0)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)
        val lectureParams = layoutParams[lectures.first()]

        assertMargins(lectureParams, 0, 0)
    }

    @Test
    fun `calculateLayoutParams for single UTC lecture sets top margin 0 (its the first lecture in all rooms, so on the top)`() {
        val startTime = 10 * 60 // 10:00am
        val lectures = listOf(createLecture(date = conferenceDate, startTime = startTime))
        val conference = Conference(firstEventStartsAt = startTime, lastEventEndsAt = startTime)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)
        val lectureParams = layoutParams[lectures.first()]

        assertMargins(lectureParams, 0, 0)
    }

    @Test
    fun `calculateLayoutParams for single *none* UTC lecture sets top margin 0 (its the first lecture in all rooms, so on the top)`() {
        val startTime = 10 * 60 // 10:00am
        val lectures = listOf(createLecture(startTime = startTime))
        val conference = Conference(firstEventStartsAt = startTime, lastEventEndsAt = startTime)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)
        val lectureParams = layoutParams[lectures.first()]

        assertMargins(lectureParams, 0, 0)
    }

    @Test
    fun `calculateLayoutParams for consecutive lecture sets margins based on gap duration`() {
        val startTime1 = 10 * 60 // 10:00am
        val duration1 = 45
        val gapMinutes = 15
        val startTime2 = startTime1 + duration1 + gapMinutes // 11:00am

        val lecture1 = createLecture(date = conferenceDate, startTime = startTime1, duration = duration1)
        val lecture2 = createLecture(date = conferenceDate, startTime = startTime2)
        val lectures = listOf(lecture1, lecture2)
        val conference = Conference(firstEventStartsAt = startTime1, lastEventEndsAt = startTime2)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)
        val lecture1Params = layoutParams[lecture1]
        val secondLectureParams = layoutParams[lecture2]

        assertMargins(lecture1Params, 0, gapMinutes)
        assertMargins(secondLectureParams, 0, 0)
    }

    @Test
    fun `calculateLayoutParams for consecutive lecture in another room sets top margin based on conference day start`() {
        /*
                         room 1             room 2
                   +---------------------------------------+
            10:00  +-------------------+                   |  +
                   |                   |                   |  |
                   |     lecture 1     |                   |  |
                   |                   |                   |  | marginTop
            10:45  +-------------------+                   |  |
                   |                   |                   |  |
            11:00  |                   +-------------------+  +
                   |                   |                   |
                   |                   |    lecture 2      |
                   |                   |                   |

        * lecture 2 follows directly lecture 1, but in another room, hence the margin includes height of lecture 1.
        */
        val duration1 = 45
        val startTime1 = 10 * 60 // 10:00am
        val startTime2 = startTime1 + duration1 + 15 // 11:00am

        val lecture2 = createLecture(date = conferenceDate, startTime = startTime2)
        val lectures = listOf(lecture2)
        val conference = Conference(firstEventStartsAt = startTime1, lastEventEndsAt = startTime2)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)
        val secondLectureParams = layoutParams[lecture2]
        val gapMinutes = 60

        assertMargins(secondLectureParams, gapMinutes, 0)
    }

    @Test
    fun `calculateLayoutParams consecutive lecture after midnight in another room`() {
        val duration1 = 45
        val startTime1 = 23 * 60 // 11:00pm
        val startTime2 = startTime1 + duration1 + 20 // 00:05am, next day

        val lecture1 = createLecture(date = conferenceDate, startTime = startTime1, duration = duration1)
        val lecture2 = createLecture(date = conferenceDate, startTime = startTime2)
        val lecturesInRoom0 = listOf(lecture1)
        val lecturesInRoom1 = listOf(lecture2)
        val conference = Conference(firstEventStartsAt = startTime1, lastEventEndsAt = startTime2)

        val layoutParamsRoom1 = layoutCalculator.calculateLayoutParams(lecturesInRoom0, conference)
        val layoutParamsRoom2 = layoutCalculator.calculateLayoutParams(lecturesInRoom1, conference)
        val lecture1Params = layoutParamsRoom1[lecture1]
        val secondLectureParams = layoutParamsRoom2[lecture2]
        val gapMinutes = 5 + 60 // 5 minutes in new day. 60 minutes on previous day, from lecture1, which starts at 11am

        assertMargins(lecture1Params, 0, 0)
        assertMargins(secondLectureParams, gapMinutes, 0)
    }

    @Test
    fun `calculateLayoutParams consecutive lecture after midnight in same room`() {
        val duration1 = 45
        val startTime1 = 23 * 60 // 11:00pm
        val startTime2 = startTime1 + duration1 + 30 // 00:15am, next day

        val lecture1 = createLecture(date = conferenceDate, startTime = startTime1, duration = duration1)
        val lecture2 = createLecture(date = conferenceDate, startTime = startTime2)
        val lectures = listOf(lecture1, lecture2)
        val conference = Conference(firstEventStartsAt = startTime1, lastEventEndsAt = startTime2)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)
        val lecture1Params = layoutParams[lecture1]
        val secondLectureParams = layoutParams[lecture2]
        val gapMinutes = 30

        assertMargins(lecture1Params, 0, gapMinutes)
        assertMargins(secondLectureParams, 0, 0)
    }

    @Test
    fun `calculateLayoutParams overlapping lecture in same room - should cut first lecture duration to match next lecture start`() {
        val duration1 = 45
        val startTime1 = 10 * 60 // 10:00am
        val startTime2 = startTime1 + duration1 - 10 // 10:35am (10 minutes overlap)

        val lecture1 = createLecture(date = conferenceDate, startTime = startTime1, duration = duration1)
        val lecture2 = createLecture(date = conferenceDate, startTime = startTime2)
        val lectures = listOf(lecture1, lecture2)
        val conference = Conference(firstEventStartsAt = startTime1, lastEventEndsAt = startTime2)

        val layoutParams = layoutCalculator.calculateLayoutParams(lectures, conference)
        val lecture1Params = layoutParams[lecture1]
        val secondLectureParams = layoutParams[lecture2]

        assertMargins(lecture1Params, 0, 0)
        assertMargins(secondLectureParams, 0, 0)
    }

    @Test
    fun `calculateLayoutParams overlapping lecture in another room - should not cut any lecture`() {
        val duration1 = 45
        val startTime1 = 10 * 60 // 10:00am
        val startTime2 = startTime1 + duration1 - 10 // 10:35am (10 minutes overlap)

        val lecture1 = createLecture(date = conferenceDate, startTime = startTime1, duration = duration1)
        val lecture2 = createLecture(date = conferenceDate, startTime = startTime2)
        val lecturesInRoom0 = listOf(lecture1)
        val lecturesInRoom1 = listOf(lecture2)
        val conference = Conference(firstEventStartsAt = startTime1, lastEventEndsAt = startTime2)

        val layoutParamsRoom1 = layoutCalculator.calculateLayoutParams(lecturesInRoom0, conference)
        val layoutParamsRoom2 = layoutCalculator.calculateLayoutParams(lecturesInRoom1, conference)
        val lecture1Params = layoutParamsRoom1[lecture1]
        val secondLectureParams = layoutParamsRoom2[lecture2]

        assertMargins(lecture1Params, 0, 0)
        assertMargins(secondLectureParams, 35, 0)
    }

    private fun assertMargins(lectureParams: LinearLayout.LayoutParams?, top: Int, bottom: Int) {
        assertThat(lectureParams!!).isNotNull()
        assertThat(lectureParams.topMargin).isEqualTo(layoutCalculator.calculateDisplayDistance(top))
        assertThat(lectureParams.bottomMargin).isEqualTo(layoutCalculator.calculateDisplayDistance(bottom))
    }

}
