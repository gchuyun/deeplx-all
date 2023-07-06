#![feature(trace_macros)]

#[macro_export]
macro_rules! nested_struct {
    // [MAIN] Primary rule to generate the struct
    (
        $(@inner)?
        $(#[$meta:meta])*
        $(@nested(#[$nested_meta:meta]))*
        $vis:vis struct $name:ident {
            $(
                $(#[$field_meta:meta])*
                $field_name:ident : $field_ty:ty $(: ($($item_ty:ident {
                    $($item_ty_inner:tt)*
                }),*))?
            ),*
        $(,)? }
    ) => {
        // Generate our primary struct
        $(#[$meta])* $(#[$nested_meta])* $vis struct $name {
            $(
                $(#[$field_meta])*
                pub $field_name : $field_ty
            ),*
        }


        // Generate our inner structs for fields
        nested_struct! {
            @inner
            $(@nested(#[$nested_meta]))*
            $vis [$($($(
                $item_ty {
                    $($item_ty_inner)*
                },
            )*)*)*]
        }
    };

    // [INCLUDE] Used to filter out struct generation to multi nested types
    (@inner $(@nested(#[$nested_meta:meta]))* $vis:vis [$name:ident {$($fields:tt)*}, $($rest:tt)*]) => {
        nested_struct! {
            @inner
            $(@nested(#[$nested_meta]))*
            $vis struct $name {
                $($fields)*
            }
        }
        nested_struct! {
            @inner
            $(@nested(#[$nested_meta]))*
            $vis [$($rest)*]
        }
    };

    // [INCLUDE] Used to filter out struct generation to only nested types
    (@inner $(@nested(#[$nested_meta:meta]))* $vis:vis $name:ident {$($fields:tt)*}) => {
        nested_struct! {
            $(@nested(#[$nested_meta]))*
            $vis struct $name {
                $($fields)*
            }
        }
    };

    // [EXCLUDE] Used to filter out struct generation to only nested types
    (@inner $(@nested(#[$nested_meta:meta]))* $vis:vis []) => {};

    // [EXCLUDE] Used to filter out struct generation to only nested types
    (@inner $(@nested(#[$nested_meta:meta]))* $vis:vis $name:ident) => {};

    // Any garbage we will ignore, including generating an invalid struct
    /* ($($other:tt)*) => {
        compile_error!(stringify!($($other)*));
    }; */
}