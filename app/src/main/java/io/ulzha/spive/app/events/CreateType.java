package io.ulzha.spive.app.events;

import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

// Should manage in SpiveInventory land? KTLO does not need type creation...
public record CreateType(
    // partition key... (Unlikely we ever need more types than fit in one instance)
    UUID typeId,
    // PL-agnostic... Namespaced akin to one of the applications that produce it? Owned by the
    // respective team
    String name,
    /* TODO @Nullable order/partitioning model... Or perhaps that's not unique per Type but Schema-ish */
    /* TODO artifactId...? Inline serdes for various programming languages, to be proliferated in code generation? Or CreateGateway may upload the artifact(s) I guess, that's where serde knowledge resides? */
    /* Unclear how portability across languages is encoded. TODO */
    // may be unset, indicates creation of a primitive type
    // field type tags must already be known (in core or created with CreateType)
    @Nullable Map<String, String> fieldTypeTags) {}
