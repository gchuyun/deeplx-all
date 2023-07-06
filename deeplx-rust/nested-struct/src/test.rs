#[macro_export]
macro_rules! data_schema {
    // 元组struct， 仅输出
    (
        $(#[$meta:meta])*
        $(@nested(#[$nested_meta:meta]))*
        $vis:vis struct $name:ident $(<$($generic:tt),*>)? ($($types:tt),* $(,)?) $(where $($where:tt),*)?
    ) => {
        $(#[$meta])*
        $(#[$nested_meta])*
        $vis struct $name $(<$($generic),*>)? ($($types),*) $(where $($where),*)?
    };
     // 单元struct， 仅输出
     (
        $(#[$meta:meta])*
        $(@nested(#[$nested_meta:meta]))*
        $vis:vis struct $name:ident $(<$($generic:tt),*>)? $(where $($where:tt),*)?
    ) => {
        $(#[$meta])*
        $(#[$nested_meta])*
        $vis struct $name $(<$($generic),*>)? $(where $($where),*)?
    };
    // 普通struct， 抽取嵌套类型
    (
        $(#[$meta:meta])*
        $(@nested(#[$nested_meta:meta]))*
        $vis:vis struct $name:ident $(<$($generic:tt),*>)? $(where $($where:tt),*)? {
            $($fields:tt),*
            $(,)?
        }
    ) => {
        data_schema!(
            @struct 
            attrs:{
                meta: ($(#[$meta])*), 
                nested_meta: ($(@nested(#[$nested_meta]))*), 
                vis: $vis,
                name: $name, 
                generic: ($(<$($generic),*>)?),
                where: ($(where $($where),*)?)                 
            },
            fields:[],
            rest_fields:{$($fields),*}
        )
    };
    (
        $(#[$meta:meta])*
        $(@nested(#[$nested_meta:meta]))*
        $vis:vis enum $name:ident $(<$($generic:tt),*>)? $(where $($where:tt),*)? {
            $($enum_items:tt),*
            $(,)?
        }
    ) => {
        data_schema!(
            @enum 
            attrs:{
                meta: ($(#[$meta])*), 
                nested_meta: ($(@nested(#[$nested_meta]))*), 
                vis: $vis,
                name: $name, 
                generic: ($(<$($generic),*>)?),
                where: ($(where $($where),*)?)                 
            },
            items:[],
            rest_body:{$($enum_items),*}
        )
    };
    // 枚举item终止
    (@enum attrs:{
            meta: ($(#[$meta:meta])*), 
            nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
            vis: $vis:vis,
            name: $name:ident, 
            generic: ($(<$($generic:tt),*>)?),
            where: ($(where $($where:tt),*)?)                 
        }, 
        items:[$(($items))*], 
        rest_body:{} 
    ) => {
        $(#[$meta])*
        $(#[$nested_meta])*
        $vis enum $name $(<$($generic),*>)? $(where $($where),*)? {
            $($items),*
        }
    }
    // A 或 A = 1
    (@enum attrs:{$($attrs:tt),*} items:[$(($items:tt))*], rest_body:{$item_name:idnet (= $item_val:expr)?, $($rest_items:tt),*} ) => {
        data_schema!(
            @enum 
            attrs:{$($attrs),*},
            items:[$(($items))* ($item_name (= $item_val)?)],
            rest_body:{$($rest_items),*}
        )
    }
    // A(T) 或 A(T) = 1 或 A(T) = 1 : (T {})
    (@enum attrs:{
            meta: ($(#[$meta:meta])*), 
            nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
            vis: $vis:vis,
            name: $name:ident, 
            generic: ($(<$($generic:tt),*>)?),
            where: ($(where $($where:tt),*)?)                 
        },  
        items:[$(($items:tt))*], 
        rest_body:{$item_name:idnet ($($tys:ty),+) (= $item_val:expr)? (: ($($defs:tt),*))?, $($rest_items:tt),*} 
    ) => {
        data_schema!(
            @enum 
            attrs:{
                meta: ($(#[$meta])*), 
                nested_meta: ($(@nested(#[$nested_meta]))*), 
                vis: $vis,
                name: $name, 
                generic: ($(<$($generic),*>)?),
                where: ($(where $($where),*)?)                 
            },
            items:[$(($items))* ($item_name ($($tys),+) (= $item_val)?)],
            rest_body:{$($rest_items),*}
        )
        data_schema!(
            @define 
            nested_meta: ($(@nested(#[$nested_meta]))*),
            vis: $vis,            
            rest_defs: ($($defs:tt),*)
        )
    }
    // A{} 或 A{} = 1
    (@enum attrs:{
            meta: ($(#[$meta:meta])*), 
            nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
            vis: $vis:vis,
            name: $name:ident, 
            generic: ($(<$($generic:tt),*>)?),
            where: ($(where $($where:tt),*)?)                 
        },  
        items:[$(($items:tt))*], 
        rest_body:{$item_name:idnet ($($tys:ty),+) (= $item_val:expr)? (: ($($defs:tt),*))?, $($rest_items:tt),*} 
    ) => {
        data_schema!(
            @enum 
            attrs:{
                meta: ($(#[$meta])*), 
                nested_meta: ($(@nested(#[$nested_meta]))*), 
                vis: $vis,
                name: $name, 
                generic: ($(<$($generic),*>)?),
                where: ($(where $($where),*)?)                 
            },
            items:[$(($items))* ($item_name ($($tys),+) (= $item_val)?)],
            rest_body:{$($rest_items),*}
        )
        data_schema!(
            @type_define 
            nested_meta: ($(@nested(#[$nested_meta]))*),
            vis: $vis,            
            rest_defs: ($($defs),*)
        )
    }
    











    (@struct_field callback: ($($callback),*), nested_meta: ($(@nested(#[$nested_meta:meta]))*), fields: [$(($fields:tt)),*], rest_fileds: {}) => {
        data_schema!($($callback),* [$($fields),*])
    }
    // f:{}
    (@struct_field 
        callback: ($($callback:tt),*), 
        nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
        owner_name: $owner_name:ident,
        fields: [$(($fields:tt)),*], 
        rest_fileds: {$(#[$field_meta:meta])* $field_vis:vis $field_name:ident : {$($inner_fields:tt),* $(,)?}, $($rest_fileds:tt),*}
    ) => {
        data_schema!(@struct_field 
            callback: ($($callback),*), 
            nested_meta: ($(@nested(#[$nested_meta]))*), 
            fields: [$(($fields))* ($(#[$field_meta])* $field_vis $field_name : concat_idents!($owner_name, __, $field_name))], 
            rest_fileds: {$($rest_fileds),*})
        data_schema!(
            @type_define 
            rest_defs: (
                $(@nested(#[$nested_meta]))* 
                $field_vis struct concat_idents!($owner_name, __, $field_name) {
                    $($inner_fields),*
                }
            )
        )
    }
    // f:F {}
    (@struct_field 
        callback: ($($callback:tt),*), 
        nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
        owner_name: $owner_name:ident,
        fields: [$(($fields:tt)),*], 
        rest_fileds: {$(#[$field_meta:meta])* $field_vis:vis $field_name:ident : $inner_type_name:ident {$($inner_fields:tt),* $(,)?}, $($rest_fileds:tt),*}
    ) => {
        data_schema!(@struct_field 
            callback: ($($callback),*), 
            nested_meta: ($(@nested(#[$nested_meta]))*), 
            fields: [$(($fields))* ($(#[$field_meta])* $field_vis $field_name : $inner_type_name)], 
            rest_fileds: {$($rest_fileds),*})
        data_schema!(
            @type_define 
            rest_defs: (
                $(@nested(#[$nested_meta]))* 
                $field_vis struct $inner_type_name {
                    $($inner_fields),*
                }
            )
        )
    }
    // f: enum F {}, f: struct F {}
    (@struct_field 
        callback: ($($callback:tt),*), 
        nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
        owner_name: $owner_name:ident,
        fields: [$(($fields:tt)),*], 
        rest_fileds: {$(#[$field_meta:meta])* $field_vis:vis $field_name:ident : $inner_type_type:ident $inner_type_name:ident {$($inner_fields:tt),* $(,)?}, $($rest_fileds:tt),*}
    ) => {
        data_schema!(@struct_field 
            callback: ($($callback),*), 
            nested_meta: ($(@nested(#[$nested_meta]))*), 
            fields: [$(($fields))* ($(#[$field_meta])* $field_vis $field_name : $inner_type_name)], 
            rest_fileds: {$($rest_fileds),*})
        data_schema!(
            @type_define 
            rest_defs: (
                $(@nested(#[$nested_meta]))* 
                $field_vis $inner_type_type $inner_type_name {
                    $($inner_fields),*
                }
            )
        )
    }
    // f: struct F
    (@struct_field 
        callback: ($($callback:tt),*), 
        nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
        owner_name: $owner_name:ident,
        fields: [$(($fields:tt)),*], 
        rest_fileds: {$(#[$field_meta:meta])* $field_vis:vis $field_name:ident : struct $inner_type_name:ident, $($rest_fileds:tt),*}
    ) => {
        data_schema!(@struct_field 
            callback: ($($callback),*), 
            nested_meta: ($(@nested(#[$nested_meta]))*), 
            fields: [$(($fields))* ($(#[$field_meta])* $field_vis $field_name : $inner_type_name)], 
            rest_fileds: {$($rest_fileds),*})
        data_schema!(
            @type_define 
            rest_defs: (
                $(@nested(#[$nested_meta]))* 
                $field_vis struct $inner_type_name
            )
        )
    }
    // f: struct F ()
    (@struct_field 
        callback: ($($callback:tt),*), 
        nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
        owner_name: $owner_name:ident,
        fields: [$(($fields:tt)),*], 
        rest_fileds: {$(#[$field_meta:meta])* $field_vis:vis $field_name:ident : struct $inner_type_name:ident ($($inner_types:ty),* $(,)?), $($rest_fileds:tt),*}
    ) => {
        data_schema!(@struct_field 
            callback: ($($callback),*), 
            nested_meta: ($(@nested(#[$nested_meta]))*), 
            fields: [$(($fields))* ($(#[$field_meta])* $field_vis $field_name : $inner_type_name)], 
            rest_fileds: {$($rest_fileds),*})
        data_schema!(
            @type_define 
            rest_defs: (
                $(@nested(#[$nested_meta]))* 
                $field_vis struct $inner_type_name ($($inner_types),*)
            )
        )
    }
    // f: F, f: F: (F{})
    (@struct_field 
        callback: ($($callback:tt),*), 
        nested_meta: ($(@nested(#[$nested_meta:meta]))*), 
        fields: [$(($fields:tt)),*], 
        rest_fileds: {$(#[$field_meta:meta])* $field_vis:vis $field_name:ident : $field_ty:ty (:($($defs:tt),*))?, $($rest_fileds:tt),*}
    ) => {
        data_schema!(@struct_field 
            callback: ($($callback),*), 
            nested_meta: ($(@nested(#[$nested_meta]))*), 
            fields: [$(($fields))* ($(#[$field_meta])* $field_vis $field_name : $field_ty)], 
            rest_fileds: {$($rest_fileds),*})
        data_schema!(
            @type_define 
            nested_meta: ($(@nested(#[$nested_meta]))*),
            rest_defs: ($($defs),*)
        )
    }
    

    (@enum_item callback: ($($callback),*), nested:{}, items: [], rest_itemss: {}) => {
        data_schema!($($callback),* [$($items),*])
    }
    (@type_define nested:{}, rest_itemss: {}) => {
        data_schema!($($callback),* [$($items),*])
    }

    (@type_define 
        nested_meta: ($(@nested(#[$nested_meta:meta]))*),
        , 
        rest_itemss: {}) => {
        data_schema!($($callback),* [$($items),*])
    }
}


f: F


f: F{}
f: F()
f: struct F
f: struct F {}
f: struct F ()
f: F: ()


@define_type name ()



$owner_name $field_vis $field_name $ty
$owner_name $field_vis $field_name {}
$owner_name $field_vis $field_name ()


$owner_name $field_vis $field_name type_name {}
$owner_name $field_vis $field_name type_name ()
$owner_name $field_vis $field_name type_name ()

