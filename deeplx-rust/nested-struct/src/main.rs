// use nested_struct::nested_struct;
// use std::collections::HashMap;
// use serde::{Serialize, Deserialize};

#![recursion_limit="1280"]
#![feature(trace_macros)]
#![feature(concat_idents)]

#[macro_use]
extern crate eager;
#[macro_export]
macro_rules! nested_types {
    // 匹配 pub struct S{}
    (
        $(#[$meta:meta])*
        $(@nested(#[$nested_meta:meta]))*
        $vis:vis struct $name:ident { $($field_name:ident : $field_ty:ty),* $(,)? }
    ) => {
        eager!{
            $(#[$meta])*
            $(@nested(#[$nested_meta]))*
            $vis struct concat_idents!(struct, _, $name) {
                $($field_name : $field_ty),*
            } 
        }
    };
}



// #[macro_use]
// extern crate eager;

eager_macro_rules!{ $eager_1
    #[macro_export]
    macro_rules! concat_ident_array {
        ($($e:ident),+ $(,)?) => { concat_idents!($($e),+ $(,)?) };
    }
}

use quote::format_ident;

trace_macros!(true);
// nested_types! {
//  #[derive(Debug)]
//  @nested(#[derive(Serialize)])
//  @nested(#[derive(Deserialize)])
 pub struct A {
//   #[serde(rename = "s")]  
  a: [i32; 8],
  b: format_ident!("Vec<{}>", B)
//   b: Vec<B> : (B {
//         bb: f32,
//     }),    
//   c: C {
//         cc: HashMap<String, D> : (
//             D { ddd: u8, }
//         ),
//         ee: enum {
//             ONE, TWO,
//         },
//   },
 }
// }


// struct A {
//     a : u8,
//     b : B: {},
//     c : C: struct {},
//     f : Vec<F> : F{},
//     g : HashMap<G, H> : (struct G{}, enum H{})
// }

// enum EE {
//     I,
//     J(u8),
//     K(KK): KK{},
//     L(L1,L2): (L1{}, enum L2{}),
//     M{
//         m1: M1: {},
//         m2: M2: (enum {}),
//         m3: Vec<M3> : (M3{}),
//         m4: String
//     }
// }

fn main() {
    // let a = A {
    //     a: [1; 8],
    //     b: vec![B{bb: 0.1}, B{bb: 0.2}],
    //     c: C{
    //         cc: "cc".to_string(),
    //         dd: HashMap::from([("dd".to_string(), D{ddd: 2})])
    //     }
    // };
    // println!("Hello, world! {:?}", a);
    // println!("json: {}", serde_json::to_string(&a).unwrap())
}
