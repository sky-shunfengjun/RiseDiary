package com.risediary.app.data.backup

import com.risediary.app.data.entity.*
import org.junit.Assert.*
import org.junit.Test

class RestoreIdentityPolicyTest {
    private val local = Flight(17,1000,61000,60,3,6f,"spurts",null,"[\"手机独有标签\"]","local",2000,9000,
        legacySpurtCount=3,legacyVolumeMl=6f,legacyVolumeInputMode="spurts",recordDraftId="local-submit",
        globalId="12345678-1234-1234-1234-123456789012")
    @Test fun olderBackupWinsWithoutChangingLocalIdentityOrSubmission() {
        val backup = local.copy(id=99,moodNote="older backup",updatedAt=3000,semenVolumeMl=4.5f,
            legacyVolumeMl=5.25f,recordDraftId="foreign-submit",globalId="87654321-4321-4321-4321-210987654321")
        val result = RestoreIdentityPolicy.mergeFlight(backup,local)
        assertEquals(17L,result.id); assertEquals(local.globalId,result.globalId)
        assertEquals("local-submit",result.recordDraftId); assertEquals("older backup",result.moodNote)
        assertEquals(3000L,result.updatedAt); assertEquals(4.5f,result.semenVolumeMl!!,0f)
        assertEquals(5.25f,result.legacyVolumeMl!!,0f)
    }
    @Test fun repeatedBackupMergeDoesNotChangeIdentityOrQuantityHistory() {
        val incoming = local.copy(id=120,moodNote="backup",updatedAt=2100)
        val first = RestoreIdentityPolicy.mergeFlight(incoming,local)
        assertEquals(first,RestoreIdentityPolicy.mergeFlight(incoming,first))
    }
    @Test fun lengthBackupKeepsLocalIdAndStableNumberWhileReplacingContents() {
        val phone = LengthRecord(7,5000,8f,12f,"phone")
        val backup = LengthRecord(7,7000,9f,13f,"backup")
        val result = RestoreIdentityPolicy.mergeLength(backup,phone)
        assertEquals(7L,result.id); assertEquals(phone.globalId,result.globalId)
        assertEquals(7000L,result.recordDate); assertEquals("backup",result.note)
        assertEquals(9f,result.flaccidLengthCm,0f)
    }
    @Test fun legacyDuplicatePreviewCountsCompareFinalContentToOriginalPhoneRecord() {
        assertEquals("skipped",restoreRecordAction("original","original"))
        assertEquals("updated",restoreRecordAction("original","last backup"))
        assertEquals("added",restoreRecordAction(null,"last backup"))
    }
}
