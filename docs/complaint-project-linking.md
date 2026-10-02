# Complaint to Municipal Work Linking

Each complaint may be linked to zero or one municipal project. A municipal project may have any number of linked complaints. This matches the existing nullable `complaints.municipal_project_id` foreign key and does not require a many-to-many join table.

Associations are created and removed only by municipal administrators or authorized ward officers. Citizens cannot attach complaints to projects. The system never infers a link from coordinates, text, issue type, severity, or AI output.

Linking an already linked complaint returns a conflict, including when the requested project is the current project. Reassignment therefore requires an explicit unlink followed by a new link. Unlinking an already unlinked complaint also returns a conflict. Neither operation deletes a complaint or project.
