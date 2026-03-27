package com.streamvault.app.ui.screens.vod

import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.VirtualGroup
import com.streamvault.domain.repository.FavoriteRepository

data class VodDialogSelection<T>(
    val selectedItem: T,
    val groupMemberships: List<Long>
)

private fun toStoredVodGroupId(groupId: Long): Long = kotlin.math.abs(groupId)

private fun toVirtualVodGroupId(groupId: Long): Long = -kotlin.math.abs(groupId)

fun matchesVodGroupMembership(storedGroupId: Long?, categoryId: Long): Boolean {
    if (storedGroupId == null) return false
    val normalizedStored = kotlin.math.abs(storedGroupId)
    val normalizedCategory = kotlin.math.abs(categoryId)
    return normalizedStored == normalizedCategory
}

suspend fun <T> loadVodDialogSelection(
    item: T,
    itemId: Long,
    contentType: ContentType,
    favoriteRepository: FavoriteRepository,
    copyWithFavorite: (T, Boolean) -> T
): VodDialogSelection<T> {
    val memberships = favoriteRepository.getGroupMemberships(itemId, contentType)
        .map(::toVirtualVodGroupId)
    val isFavorite = favoriteRepository.isFavorite(itemId, contentType)
    return VodDialogSelection(
        selectedItem = copyWithFavorite(item, isFavorite),
        groupMemberships = memberships
    )
}

suspend fun setVodFavorite(
    itemId: Long,
    contentType: ContentType,
    isFavorite: Boolean,
    favoriteRepository: FavoriteRepository
) {
    if (isFavorite) {
        favoriteRepository.addFavorite(itemId, contentType)
    } else {
        favoriteRepository.removeFavorite(itemId, contentType)
    }
}

suspend fun updateVodGroupMembership(
    itemId: Long,
    groupId: Long,
    contentType: ContentType,
    shouldBeMember: Boolean,
    favoriteRepository: FavoriteRepository
): List<Long> {
    val encodedGroupId = toStoredVodGroupId(groupId)
    if (shouldBeMember) {
        favoriteRepository.addFavorite(itemId, contentType, groupId = encodedGroupId)
    } else {
        favoriteRepository.removeFavorite(itemId, contentType, groupId = encodedGroupId)
    }
    return favoriteRepository.getGroupMemberships(itemId, contentType)
        .map(::toVirtualVodGroupId)
}

suspend fun createVodGroup(
    name: String,
    contentType: ContentType,
    favoriteRepository: FavoriteRepository
): Result<VirtualGroup> =
    favoriteRepository.createGroup(name, contentType = contentType)
