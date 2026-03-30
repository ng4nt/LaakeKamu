package fi.hh.laakekamu.data

import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Tyyppi
import fi.hh.laakekamu.data.entity.Yksikko
import fi.hh.laakekamu.domain.EditedTime
import fi.hh.laakekamu.domain.NameError
import fi.hh.laakekamu.domain.validateName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** In-memory [LaakeRepository] for ViewModel unit tests. Uses the real name rule. */
class FakeLaakeRepository(
    val tyypit: List<Tyyppi> = listOf(Tyyppi(1, "tabletti"), Tyyppi(2, "kapseli")),
    val yksikot: List<Yksikko> = listOf(Yksikko("mg"), Yksikko("ml"))
) : LaakeRepository {
    val laakkeet = MutableStateFlow<List<Laake>>(emptyList())
    val added = mutableListOf<Pair<Laake, List<Int>>>()

    override fun observeTyypit(): Flow<List<Tyyppi>> = flowOf(tyypit)

    override fun observeYksikot(): Flow<List<Yksikko>> = flowOf(yksikot)

    override fun observeActive(): Flow<List<Laake>> = laakkeet.map { list ->
        list.filter { it.aktiivinen }
    }

    override suspend fun currentTimes(laakeId: Long): List<Muistutus> = emptyList()

    override suspend fun nameError(name: String, editedId: Long?): NameError? = validateName(
        name,
        laakkeet.value.filter {
            it.aktiivinen
        }.associate { it.laakeId to it.nimi },
        editedId
    )

    override suspend fun add(laake: Laake, times: List<Int>): Long {
        val id = (laakkeet.value.maxOfOrNull { it.laakeId } ?: 0) + 1
        laakkeet.value = laakkeet.value + laake.copy(laakeId = id)
        added += laake to times
        return id
    }

    override suspend fun update(laake: Laake, times: List<EditedTime>) {
        laakkeet.value = laakkeet.value.map { if (it.laakeId == laake.laakeId) laake else it }
    }

    override suspend fun delete(laakeId: Long) {
        laakkeet.value =
            laakkeet.value.map { if (it.laakeId == laakeId) it.copy(aktiivinen = false) else it }
    }
}
