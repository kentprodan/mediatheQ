package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.datastore.SavedQueueFlag

class FakeSavedQueueFlag(
    override var isSet: Boolean = false,
) : SavedQueueFlag
