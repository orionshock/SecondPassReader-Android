package com.secondpasslibrary.reader.home

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class HomeRefreshAvailabilityTracker {
    private val channel = Channel<HomeRefreshAvailability>(Channel.CONFLATED)
    val changes: Flow<HomeRefreshAvailability> = channel.receiveAsFlow()

    private var recent = ProjectionAvailability.IDLE
    private var shelves = ProjectionAvailability.IDLE
    private var published: HomeRefreshAvailability? = null

    fun reset() {
        recent = ProjectionAvailability.IDLE
        shelves = ProjectionAvailability.IDLE
        published = null
    }

    fun recentStarted() {
        recent = ProjectionAvailability.REFRESHING
        publish()
    }

    fun recentCompleted(refresh: HomeProjectionRefresh) {
        recent = refresh.toProjectionAvailability()
        publish()
    }

    fun shelvesStarted() {
        shelves = ProjectionAvailability.REFRESHING
        publish()
    }

    fun shelvesCompleted(refresh: HomeProjectionRefresh) {
        shelves = refresh.toProjectionAvailability()
        publish()
    }

    private fun publish() {
        val next =
            when {
                recent == ProjectionAvailability.REFRESHING ||
                    shelves == ProjectionAvailability.REFRESHING ->
                    HomeRefreshAvailability.REFRESHING

                recent == ProjectionAvailability.UNREACHABLE ||
                    shelves == ProjectionAvailability.UNREACHABLE ->
                    HomeRefreshAvailability.UNREACHABLE

                recent == ProjectionAvailability.REACHABLE ||
                    shelves == ProjectionAvailability.REACHABLE ->
                    HomeRefreshAvailability.REACHABLE

                else -> return
            }
        if (published == next) return
        published = next
        channel.trySend(next)
    }
}

private fun HomeProjectionRefresh.toProjectionAvailability(): ProjectionAvailability = when (this) {
    HomeProjectionRefresh.Refreshing -> ProjectionAvailability.REFRESHING

    is HomeProjectionRefresh.Failed ->
        if (reason == HomeProjectionFailure.Unreachable) {
            ProjectionAvailability.UNREACHABLE
        } else {
            ProjectionAvailability.REACHABLE
        }

    HomeProjectionRefresh.Current,
    HomeProjectionRefresh.Idle -> ProjectionAvailability.REACHABLE
}

private enum class ProjectionAvailability {
    IDLE,
    REFRESHING,
    REACHABLE,
    UNREACHABLE
}
