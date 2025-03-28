package social.bigbone.api.method

import io.mockk.slot
import io.mockk.verify
import org.amshove.kluent.AnyException
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainAll
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.jupiter.api.Test
import social.bigbone.MastodonClient.Method
import social.bigbone.Parameters
import social.bigbone.PrecisionDateTime.ValidPrecisionDateTime.ExactTime
import social.bigbone.api.Range
import social.bigbone.api.entity.NotificationType
import social.bigbone.testtool.MockClient
import java.time.Instant

class GroupedNotificationMethodsTest {

    @Test
    fun testGroupedNotificationResponseParsing() {
        val client = MockClient.mock("grouped_notifications_get_all_success.json")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        val pageable = groupedNotificationMethods.getAllGroupedNotifications(
            range = Range(limit = 2)
        ).execute()

        with(pageable.part.first()) {
            with(accounts) {
                shouldNotBeNull()
                shouldHaveSize(4)
                with(first()) {
                    id shouldBeEqualTo "16"
                    username shouldBeEqualTo "eve"
                    acct shouldBeEqualTo "eve"
                }
                with(get(1)) {
                    id shouldBeEqualTo "3547"
                    username shouldBeEqualTo "alice"
                    acct shouldBeEqualTo "alice"
                }
            }
            with(statuses) {
                shouldNotBeNull()
                shouldHaveSize(2)
                with(first()) {
                    id shouldBeEqualTo "113010503322889311"
                    createdAt shouldBeEqualTo ExactTime(Instant.parse("2024-08-23T08:57:12.057Z"))
                    with(account) {
                        shouldNotBeNull()
                        id shouldBeEqualTo "55911"
                        username shouldBeEqualTo "user"
                        acct shouldBeEqualTo "user"
                    }
                }
            }
            with(notificationGroups) {
                shouldHaveSize(2)
                with(first()) {
                    groupKey shouldBeEqualTo "favourite-113010503322889311-479000"
                    notificationsCount shouldBeEqualTo 2
                    type shouldBeEqualTo NotificationType.FAVOURITE
                    mostRecentNotificationId shouldBeEqualTo 196_014
                    pageMinId shouldBeEqualTo "196013"
                    pageMaxId shouldBeEqualTo "196014"
                    latestPageNotificationAt shouldBeEqualTo ExactTime(Instant.parse("2024-08-23T08:59:56.743Z"))
                    with(sampleAccountIds) {
                        shouldHaveSize(2)
                        get(0) shouldBeEqualTo "16"
                        get(1) shouldBeEqualTo "3547"
                    }
                    statusId shouldBeEqualTo "113010503322889311"
                }
            }
        }
    }

    @Test
    fun `When getting all grouped notifications with all parameters, then call endpoint with correct parameters`() {
        val client = MockClient.mock("grouped_notifications_get_all_success.json")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        val includeTypes = listOf(NotificationType.FOLLOW, NotificationType.MENTION)
        val excludeTypes = listOf(NotificationType.FAVOURITE)
        val groupedTypes = listOf(NotificationType.FOLLOW)
        groupedNotificationMethods.getAllGroupedNotifications(
            includeTypes = includeTypes,
            excludeTypes = excludeTypes,
            accountId = "1234567",
            expandAccounts = GroupedNotificationMethods.ExpandAccounts.FULL,
            groupedTypes = groupedTypes,
            includeFiltered = true,
            range = Range(
                minId = "23",
                maxId = "42",
                sinceId = "7",
                limit = 2
            )
        ).execute()

        val parametersCapturingSlot = slot<Parameters>()
        verify {
            client.get(
                path = "api/v2/notifications",
                query = capture(parametersCapturingSlot)
            )
        }
        with(parametersCapturingSlot.captured) {
            parameters["types[]"]?.shouldContainAll(includeTypes.map(NotificationType::apiName))
            parameters["exclude_types[]"]?.shouldContainAll(excludeTypes.map(NotificationType::apiName))
            parameters["grouped_types[]"]?.shouldContainAll(groupedTypes.map(NotificationType::apiName))
            parameters["account_id"]?.shouldContainAll(listOf("1234567"))

            toQuery() shouldBeEqualTo "max_id=42" +
                "&min_id=23" +
                "&since_id=7" +
                "&limit=2" +
                "&types[]=follow" +
                "&types[]=mention" +
                "&exclude_types[]=favourite" +
                "&account_id=1234567" +
                "&expand_accounts=full" +
                "&grouped_types[]=follow" +
                "&include_filtered=true"
        }
    }

    @Test
    fun `When getting grouped notifications, then call endpoint with correct parameters`() {
        val client = MockClient.mock("grouped_notifications_get_single_success.json")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        groupedNotificationMethods.getGroupedNotification(groupKey = "favourite-113010503322889311-479000").execute()

        verify {
            client.get(
                path = "api/v2/notifications/favourite-113010503322889311-479000"
            )
        }
    }

    @Test
    fun `Given a client returning success, when dismissing a specific notification, then expect no exceptions and verify correct method was called`() {
        val client = MockClient.mock(jsonName = "notifications_dismiss_success.json")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        invoking {
            groupedNotificationMethods.dismissNotification("favourite-113010503322889311-479000")
        } shouldNotThrow AnyException

        verify {
            client.performAction(
                endpoint = "api/v2/notifications/favourite-113010503322889311-479000/dismiss",
                method = Method.POST
            )
        }
    }

    @Test
    fun `When getting accounts of all notifications in a group, then call endpoint with correct parameters`() {
        val client = MockClient.mock("grouped_notifications_get_accounts_of_notifications_success.json")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        groupedNotificationMethods.getAccountsOfAllNotificationsInGroup(
            groupKey = "favourite-113010503322889311-479000"
        ).execute()

        verify {
            client.get(
                path = "api/v2/notifications/favourite-113010503322889311-479000/accounts"
            )
        }
    }

    @Test
    fun `Given a client returning success, when getting number of unread notifications with parameters, then call endpoint with correct parameter`() {
        val client = MockClient.mockClearText("""{"count": 42}""")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        groupedNotificationMethods.getNumberOfUnreadNotifications(
            limit = 450,
            types = listOf(NotificationType.MENTION, NotificationType.REBLOG),
            excludeTypes = emptyList(),
            accountId = null,
            groupedTypes = listOf(NotificationType.REBLOG)
        ).execute()

        val parametersCapturingSlot = slot<Parameters>()
        verify {
            client.get(
                path = "api/v2/notifications/unread_count",
                query = capture(parametersCapturingSlot)
            )
        }
        with(parametersCapturingSlot.captured) {
            toQuery() shouldBeEqualTo "limit=450&types[]=mention&types[]=reblog&types[]=reblog"
        }
    }

    @Test
    fun `Given a client returning success, when getting number of unread notifications, then return UnreadNotificationCount`() {
        val client = MockClient.mockClearText("""{"count": 42}""")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        val unreadNotifications = groupedNotificationMethods.getNumberOfUnreadNotifications().execute()

        unreadNotifications.count shouldBeEqualTo 42
    }

    @Test
    fun `Given a client returning success, when attempting get number of unread notifications with too high limit, then throw IllegalArgumentException`() {
        val client = MockClient.mockClearText("""{"count": 42}""")
        val groupedNotificationMethods = GroupedNotificationMethods(client)

        invoking {
            groupedNotificationMethods.getNumberOfUnreadNotifications(limit = 9_001).execute()
        } shouldThrow IllegalArgumentException::class withMessage "Limit must be no larger than 1000 but was 9001"
    }
}
