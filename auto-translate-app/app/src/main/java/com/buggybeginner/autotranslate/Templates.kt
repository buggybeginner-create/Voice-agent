package com.buggybeginner.autotranslate

object Templates {
    val list: List<Pair<String, String>> = listOf(
        "Leave" to "Dear Sir/Madam,\n\nI would like to request leave from [start date] to [end date] due to [reason]. Kindly grant me leave for this period.\n\nThank you.\n[Your name]",
        "Follow-up" to "Hello [Name],\n\nI hope you are doing well. I am following up on my previous message regarding [topic]. Could you please share an update when you get a chance?\n\nThank you.\n[Your name]",
        "Invoice reminder" to "Hello [Name],\n\nThis is a gentle reminder that invoice no. [number] for [amount] is due on [date]. Please let me know once the payment is done.\n\nThank you.\n[Your name]",
        "Complaint" to "Dear Sir/Madam,\n\nI am writing to complain about [issue]. I ordered/used [product or service] on [date] and faced [problem]. Kindly resolve this at the earliest.\n\nRegards,\n[Your name]",
        "Thank you" to "Hello [Name],\n\nThank you so much for your help with [topic]. I really appreciate your time and support.\n\nBest regards,\n[Your name]",
        "Meeting" to "Hello [Name],\n\nCould we schedule a meeting on [date] at [time] to discuss [topic]? Please let me know if this works for you.\n\nThank you.\n[Your name]"
    )
}
