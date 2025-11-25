package com.example.appcolegios.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Repositorio centralizado para accesos a Cloud Firestore.
 * Implementa operaciones suspend y helpers que usan las colecciones:
 * users, students, parents, teachers, groups, Grades, tasks, events,
 * notifications, transportAttendance, chats, auth_queue, announcements.
 *
 * Este archivo es una implementación inicial: puede ampliarse con Flows y listeners.
 */
class FirestoreRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // Maximum allowed string size to attempt writing to Firestore for a single field (conservative)
    private val MAX_STRING_FIELD_SIZE = 200_000 // bytes/chars

    /**
     * Removes fields that are likely to contain Base64 blobs or are too large to safely write to Firestore.
     * Public for testing - use sanitizeFields before performing writes.
     */
    fun sanitizeFields(fields: Map<String, Any?>): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for ((k, v) in fields) {
            if (k.contains("base64", ignoreCase = true)) {
                // Skip base64 fields entirely
                continue
            }
            if (v is String) {
                if (v.length > MAX_STRING_FIELD_SIZE) {
                    // skip extremely large strings to avoid Firestore document size limit
                    continue
                }
            }
            out[k] = v
        }
        return out
    }

    // Constantes de colección
    private val COL_USERS = "users"
    private val COL_STUDENTS = "students"
    private val COL_PARENTS = "parents"
    private val COL_TEACHERS = "teachers"
    private val COL_GROUPS = "groups"
    private val COL_GRADES = "Grades"
    private val COL_TASKS = "tasks"
    private val COL_EVENTS = "events"
    private val COL_ANNOUNCEMENTS = "announcements"
    private val COL_NOTIFICATIONS = "notifications"
    private val COL_TRANSPORT = "transportAttendance"
    private val COL_CHATS = "chats"
    private val COL_AUTH_QUEUE = "auth_queue"

    fun currentUserId(): String? = auth.currentUser?.uid

    // Helper: obtener documento como mapa de una colección por id
    suspend fun getDocumentData(collection: String, docId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val snap = db.collection(collection).document(docId).get().await()
        return@withContext if (snap.exists()) snap.data else null
    }

    // Helper: buscar primer documento por email en una colección
    suspend fun queryDocumentByEmail(collection: String, email: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val q = db.collection(collection).whereEqualTo("email", email).limit(1L).get().await()
        return@withContext if (!q.isEmpty) q.documents[0].data else null
    }

    // Helper: query whereArrayContains, devuelve lista de mapas incluyendo "__id" con el id del documento
    suspend fun queryWhereArrayContains(collection: String, field: String, value: Any): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val q = db.collection(collection).whereArrayContains(field, value).get().await()
        return@withContext q.documents.map { (it.data ?: emptyMap<String, Any?>()) + mapOf("__id" to it.id) }
    }

    // Helper: query whereEqualTo, devuelve lista de mapas incluyendo "__id"
    suspend fun queryWhereEqual(collection: String, field: String, value: Any): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val q = db.collection(collection).whereEqualTo(field, value).get().await()
        return@withContext q.documents.map { (it.data ?: emptyMap<String, Any?>()) + mapOf("__id" to it.id) }
    }

    // Helper: obtener documentos de una subcolección (p.ej. students/{uid}/grades)
    suspend fun getSubcollectionDocuments(parentCollection: String, parentId: String, subcollection: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val q = db.collection(parentCollection).document(parentId).collection(subcollection).get().await()
        return@withContext q.documents.map { (it.data ?: emptyMap<String, Any?>()) + mapOf("__id" to it.id) }
    }

    // Helper: obtener documentos de una coleccion ordenada por un campo y opcionalmente con cutoff de fecha
    suspend fun queryCollectionOrderedWithOptionalCutoff(collection: String, orderByField: String, cutoff: com.google.firebase.Timestamp? = null, limit: Long? = null): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        var q = db.collection(collection).orderBy(orderByField, com.google.firebase.firestore.Query.Direction.DESCENDING)
        if (cutoff != null) q = q.whereGreaterThanOrEqualTo(orderByField, cutoff)
        if (limit != null) q = q.limit(limit)
        val snap = q.get().await()
        return@withContext snap.documents.map { (it.data ?: emptyMap<String, Any?>()) + mapOf("__id" to it.id) }
    }

    // --- USERS ---
    suspend fun createOrUpdateUser(
        uid: String,
        email: String,
        fullName: String,
        role: String,
        groupId: String? = null,
        photoUrl: String? = null,
        phone: String? = null,
        isActive: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val userDoc = db.collection(COL_USERS).document(uid)
        val data = hashMapOf<String, Any?>(
            "uid" to uid,
            "email" to email,
            "fullName" to fullName,
            "role" to role,
            "groupId" to (groupId ?: ""),
            "photoUrl" to (photoUrl ?: ""),
            "phone" to (phone ?: ""),
            "isActive" to isActive,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val safe = sanitizeFields(data)
        userDoc.set(safe, SetOptions.merge()).await()
        // Ensure createdAt exists (set if not present)
        userDoc.update("createdAt", FieldValue.serverTimestamp()).addOnFailureListener { /* ignore */ }
    }

    /**
     * Create or update the user document and the role-specific document.
     * Ensures groups and Grades entries are created for students.
     */
    suspend fun createUserAndRole(
        uid: String,
        email: String,
        fullName: String,
        role: String,
        groupId: String? = null,
        photoUrl: String? = null,
        phone: String? = null,
        parentIds: List<String> = emptyList(),
        subjects: List<String> = emptyList(),
        teacherGroups: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        // Create or update base user doc
        createOrUpdateUser(uid, email, fullName, role, groupId, photoUrl, phone)

        // Create role-specific document
        when (role.uppercase()) {
            "STUDENT" -> {
                val g = groupId ?: ""
                createOrUpdateStudentDocument(uid, fullName, g, parentIds)
            }
            "PARENT" -> {
                createOrUpdateParentDocument(uid, fullName, parentIds)
            }
            "TEACHER" -> {
                createOrUpdateTeacherDocument(uid, fullName, subjects, teacherGroups)
                // ensure teacher groups exist
                for (grp in teacherGroups) if (grp.isNotBlank()) ensureGroupExists(grp)
            }
            else -> {
                // ADMIN or unknown: nothing else required
            }
        }
    }

    // --- ROLE DOCUMENTS ---
    suspend fun createOrUpdateStudentDocument(
        uid: String,
        fullName: String,
        groupId: String,
        parentIds: List<String> = emptyList(),
        birthDate: String? = null,
        medicalInfo: String? = null,
        address: String? = null
    ) = withContext(Dispatchers.IO) {
        val studentRef = db.collection(COL_STUDENTS).document(uid)
        val data = hashMapOf<String, Any?>(
            "fullName" to fullName,
            "groupId" to groupId,
            "parentIds" to parentIds,
            "birthDate" to (birthDate ?: ""),
            "medicalInfo" to (medicalInfo ?: ""),
            "address" to (address ?: ""),
            "createdAt" to FieldValue.serverTimestamp()
        )
        studentRef.set(sanitizeFields(data), SetOptions.merge()).await()

        // Solo crear/asegurar grupo y entrada en Grades si groupId no está vacío
        if (groupId.isNotBlank()) {
            ensureGroupExists(groupId, mapOf("name" to groupId))
            ensureGradeGroupAndStudentEntry(groupId, uid, fullName)
        }
    }

    suspend fun createOrUpdateParentDocument(
        uid: String,
        fullName: String,
        childrenIds: List<String> = emptyList(),
        phone: String? = null,
        address: String? = null
    ) = withContext(Dispatchers.IO) {
        val parentRef = db.collection(COL_PARENTS).document(uid)
        val data = hashMapOf<String, Any?>(
            "fullName" to fullName,
            "childrenIds" to childrenIds,
            "phone" to (phone ?: ""),
            "address" to (address ?: "")
        )
        parentRef.set(sanitizeFields(data), SetOptions.merge()).await()
    }

    suspend fun createOrUpdateTeacherDocument(
        uid: String,
        fullName: String,
        subjects: List<String> = emptyList(),
        groups: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val teacherRef = db.collection(COL_TEACHERS).document(uid)
        val data = hashMapOf<String, Any?>(
            "fullName" to fullName,
            "subjects" to subjects,
            "groups" to groups
        )
        teacherRef.set(sanitizeFields(data), SetOptions.merge()).await()
    }

    // --- GROUPS and GRADES ---
    private suspend fun ensureGroupExists(groupId: String, metadata: Map<String, Any?> = emptyMap()) = withContext(Dispatchers.IO) {
        val groupRef = db.collection(COL_GROUPS).document(groupId)
        val snapshot = groupRef.get().await()
        if (!snapshot.exists()) {
            val groupData = hashMapOf<String, Any?>(
                "groupId" to groupId,
                "name" to (metadata["name"] ?: groupId),
                "createdAt" to FieldValue.serverTimestamp()
            )
            groupRef.set(sanitizeFields(groupData)).await()
        }
    }

    // Public wrapper para asegurar existencia de grupo (utilizable desde UI/ViewModels)
    suspend fun ensureGroupExistsPublic(groupId: String, metadata: Map<String, Any?> = emptyMap()) = withContext(Dispatchers.IO) {
        if (groupId.isBlank()) return@withContext
        ensureGroupExists(groupId, metadata)
    }

    private suspend fun ensureGradeGroupAndStudentEntry(groupId: String, uid: String, fullName: String) = withContext(Dispatchers.IO) {
        val gradeGroupRef = db.collection(COL_GRADES).document(groupId)
        val snap = gradeGroupRef.get().await()
        if (!snap.exists()) {
            val data = hashMapOf<String, Any?>(
                "groupId" to groupId,
                "groupName" to groupId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            gradeGroupRef.set(data).await()
        }
        val studentSummaryRef = gradeGroupRef.collection("students").document(uid)
        val studentData = hashMapOf<String, Any?>(
            "studentId" to uid,
            "fullName" to fullName,
            "groupId" to groupId,
            "averageScore" to 0.0,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        studentSummaryRef.set(sanitizeFields(studentData), SetOptions.merge()).await()
    }

    // --- GRADES: write grade and recalc average ---
    suspend fun writeStudentGradeAndRecalculateAverage(
        studentUid: String,
        groupId: String,
        gradeId: String,
        subject: String,
        activity: String,
        score: Double,
        maxScore: Double,
        period: String
    ) = withContext(Dispatchers.IO) {
        val gradeRef = db.collection(COL_STUDENTS)
            .document(studentUid)
            .collection("grades")
            .document(gradeId)

        val gradeData = hashMapOf<String, Any?>(
            "subject" to subject,
            "activity" to activity,
            "score" to score,
            "maxScore" to maxScore,
            "period" to period,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        gradeRef.set(sanitizeFields(gradeData), SetOptions.merge()).await()

        // Recalculate average across all grades for that student
        val gradesSnap = db.collection(COL_STUDENTS).document(studentUid).collection("grades").get().await()
        var total = 0.0
        var count = 0
        for (doc in gradesSnap.documents) {
            val s = doc.getDouble("score") ?: 0.0
            val m = doc.getDouble("maxScore") ?: 0.0
            if (m > 0.0) {
                total += (s / m) * 100.0 // percent-based
                count++
            }
        }
        val average = if (count > 0) total / count else 0.0

        // Ensure Grades group student entry exists and update averageScore
        val studentSummaryRef = db.collection(COL_GRADES).document(groupId).collection("students").document(studentUid)
        val summaryData = hashMapOf<String, Any?>(
            "averageScore" to average,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        studentSummaryRef.set(sanitizeFields(summaryData), SetOptions.merge()).await()
    }

    // --- TASKS / EVENTS / ANNOUNCEMENTS ---
    suspend fun createTaskGlobal(
        title: String,
        description: String,
        groupId: String?,
        subject: String?,
        dueDate: Date?,
        createdBy: String
    ) = withContext(Dispatchers.IO) {
        val doc = db.collection(COL_TASKS).document()
        val data = hashMapOf<String, Any?>(
            "title" to title,
            "description" to description,
            "groupId" to (groupId ?: ""),
            "subject" to (subject ?: ""),
            "dueDate" to (dueDate ?: null),
            "createdBy" to createdBy,
            "createdAt" to FieldValue.serverTimestamp()
        )
        doc.set(sanitizeFields(data)).await()
    }

    suspend fun createEventGlobal(
        title: String,
        description: String,
        date: Date?,
        groupId: String?,
        location: String?,
        createdBy: String
    ) = withContext(Dispatchers.IO) {
        val doc = db.collection(COL_EVENTS).document()
        val data = hashMapOf<String, Any?>(
            "title" to title,
            "description" to description,
            "date" to (date ?: null),
            "groupId" to (groupId ?: ""),
            "location" to (location ?: ""),
            "createdBy" to createdBy,
            "createdAt" to FieldValue.serverTimestamp()
        )
        doc.set(sanitizeFields(data)).await()
    }

    suspend fun createAnnouncement(
        title: String,
        body: String,
        audience: String,
        createdBy: String
    ) = withContext(Dispatchers.IO) {
        val doc = db.collection(COL_ANNOUNCEMENTS).document()
        val data = hashMapOf<String, Any?>(
            "title" to title,
            "body" to body,
            "audience" to audience,
            "createdBy" to createdBy,
            "createdAt" to FieldValue.serverTimestamp()
        )
        doc.set(sanitizeFields(data)).await()
    }

    suspend fun createNotification(
        title: String,
        body: String,
        type: String,
        audience: String,
        createdBy: String
    ) = withContext(Dispatchers.IO) {
        val doc = db.collection(COL_NOTIFICATIONS).document()
        val data = hashMapOf<String, Any?>(
            "title" to title,
            "body" to body,
            "type" to type,
            "audience" to audience,
            "createdBy" to createdBy,
            "createdAt" to FieldValue.serverTimestamp()
        )
        doc.set(sanitizeFields(data)).await()
    }

    // --- TRANSPORT ATTENDANCE ---
    suspend fun writeTransportAttendance(
        studentId: String,
        studentName: String,
        groupId: String,
        date: String,
        status: String,
        route: String?,
        registeredBy: String
    ) = withContext(Dispatchers.IO) {
        val doc = db.collection(COL_TRANSPORT).document()
        val data = hashMapOf<String, Any?>(
            "studentId" to studentId,
            "studentName" to studentName,
            "groupId" to groupId,
            "date" to date,
            "status" to status,
            "route" to (route ?: ""),
            "timestamp" to FieldValue.serverTimestamp(),
            "registeredBy" to registeredBy
        )
        doc.set(sanitizeFields(data)).await()
    }

    // --- CHATS / INBOX ---
    suspend fun ensureChatAndAddMessage(
        chatId: String,
        participants: List<String>,
        fromId: String,
        toId: String,
        text: String
    ) = withContext(Dispatchers.IO) {
        val chatRef = db.collection(COL_CHATS).document(chatId)
        val chatData = hashMapOf<String, Any?>(
            "participants" to participants,
            "lastMessage" to text,
            "lastTimestamp" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        chatRef.set(chatData, SetOptions.merge()).await()

        val messageRef = chatRef.collection("messages").document()
        val msg = hashMapOf<String, Any?>(
            "fromId" to fromId,
            "toId" to toId,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp(),
            "read" to false
        )
        messageRef.set(msg).await()

        // Update inbox entries for both participants
        updateInboxEntryFor(chatRef.id, fromId, toId, text)
        updateInboxEntryFor(chatRef.id, toId, fromId, text)
    }

    private suspend fun updateInboxEntryFor(chatId: String, ownerId: String, otherUserId: String, lastMessage: String) = withContext(Dispatchers.IO) {
        val otherUserSnap = db.collection(COL_USERS).document(otherUserId).get().await()
        val otherName = otherUserSnap.getString("fullName") ?: otherUserSnap.getString("displayName") ?: otherUserId
        val otherAvatar = otherUserSnap.getString("photoUrl") ?: otherUserSnap.getString("avatarUrl") ?: ""

        val inboxRef = db.collection(COL_USERS).document(ownerId).collection("inbox").document(otherUserId)
        val inboxData = hashMapOf<String, Any?>(
            "otherUserId" to otherUserId,
            "otherUserName" to otherName,
            "otherUserAvatarUrl" to otherAvatar,
            "lastMessage" to lastMessage,
            "lastTimestamp" to FieldValue.serverTimestamp()
        )
        inboxRef.set(sanitizeFields(inboxData), SetOptions.merge()).await()
    }

    // --- AUTH QUEUE ---
    suspend fun pushAuthQueueRequest(email: String, role: String, groupId: String?) = withContext(Dispatchers.IO) {
        val doc = db.collection(COL_AUTH_QUEUE).document()
        val data = hashMapOf<String, Any?>(
            "email" to email,
            "role" to role,
            "groupId" to (groupId ?: ""),
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp()
        )
        doc.set(sanitizeFields(data)).await()
    }

    // Helper: contar documentos en una colección (rápido)
    suspend fun countCollectionDocuments(collection: String): Int = withContext(Dispatchers.IO) {
        val snap = db.collection(collection).get().await()
        return@withContext snap.size()
    }

    // Helper: set/merge fields on a document
    suspend fun setDocumentFields(collection: String, docId: String, fields: Map<String, Any?>) = withContext(Dispatchers.IO) {
        db.collection(collection).document(docId).set(sanitizeFields(fields), SetOptions.merge()).await()
    }

    // Helper: delete specific fields from a document using FieldValue.delete()
    suspend fun deleteFields(collection: String, docId: String, fields: List<String>) = withContext(Dispatchers.IO) {
        if (fields.isEmpty()) return@withContext
        val map = mutableMapOf<String, Any?>()
        for (f in fields) map[f] = FieldValue.delete()
        db.collection(collection).document(docId).update(map).await()
    }

    // Helper: get all documents in a top-level collection (data maps with '__id')
    suspend fun getAllDocuments(collection: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val snap = db.collection(collection).get().await()
        return@withContext snap.documents.map { (it.data ?: emptyMap<String, Any?>()) + mapOf("__id" to it.id) }
    }
}
