# ヿ

ヿ (こと, /koto/) は、第一級の型と動的型検査を備えた異種システムプログラミング言語です。

## 高レベルの目標

- リアルタイム性が求められるソフトウェア（OS、ゲームエンジン、オーディオプロセッサー）に適した、低レベルで予測可能な制御を提供します。
- 異なる実行環境におけるコード共有や連携を可能にします。（CPU/GPUなど）
- 第一級の実行時コンパイル機能を提供します。
- 可能な限りユーザーが手動で証明を書かずに済むようにします。
- 定理証明器にはしません（論理的整合性は要求しません）。
- プログラムの正しさについて、完全な静的保証は行いません。
- ソルバーやプロパティーベーステストによるベストエフォートの実行前検証を行い、警告や実行時エラーも併用してプログラムの正しさを高めます。
- リッチなエディター/IDEサポートを提供します。
- 信頼できるエディター/ツールサポートを不可能にしたり脆くしたりする言語機能は追加しません。
- 外部のツール/プリプロセッサーを不要にします。それらが必要になるのは、言語機能が不足していることの兆候です。
- LLVMやCraneliftなどのコンパイラー基盤に依存しません。
- トレーシングGCによる自動メモリ管理は行いません。

## 例

```ヿ
# line comment

let zero = 0
let zero : int64 = 0
let b = bool
let t = true
let f = false
let float = float64
let pi = 3.14159
let -pi = -3.14159
let pi : float64 = 3.14159

let pair = 1, 2
let pair : int64, int64 = 1, 2
let pair3 = 1, 2, 3
let pair3 : int64, int64, int64 = 1, 2, 3
let pair3 : int64, (int64, int64) = 1, 2, 3
let pair3 : (int64, int64), int64 = (1, 2), 3
let d-pair : a : type, a = int64, 1
let d-pair4 : a : type, b : type, a, b = bool, int64, true, 1

let func : int64 → int64 = x → x
let func3 : int64 → int64 → int64 = x → y → y
let func3 : int64 → (int64 → int64) = x → y → y
let func3 : (int64 → int64) → int64 = f → f(0)
let d-func = a : type → a
let d-func3 = a : type → b : type → b

let refine-type : type = int64 @ true
let refine-type : type = x : bool @ x
let refine-trivial : int64 @ true = 1
let refine-compute : x : int64 @ (y : int64 → true)(x) = 1
# let refine-compute-fail : x : int64 @ (y : int64 → false)(x) = 1
# let refine-singleton : x : bool @ x = true

# let id = x → x
let id = x : int64 → x
let id : int64 → int64 = x → x
let id : int64 → int64 = x : int64 → x
let - : int64 = id(1)

let d-id  = a-t : type → a : a-t → a
let - : int64 → int64 = d-id(int64)
let - : int64 = d-id(int64)(3)
let - : bool = d-id(bool)(true)

type
```

## 対応プラットフォーム

### ティア1

- [ ] WebAssembly Browser WebGPU
- [ ] x86-64 Windows Vulkan

### ティア2

- [ ] AArch64 Linux Vulkan
- [ ] x86-64 Linux Vulkan
- [ ] AArch64 Windows Vulkan

## ランダムなアイデア

- 全ては式。
- LL(1)文法を採用すると、ロジットバイアスによって自己回帰テキストモデルが必ず構文的に正しいコードを生成できるようになる。
    - エラーの無いコードの最後にトークンを追加しても、エラーが発生しない性質（一時的な構文エラーを除く）。
- この言語に関するデータセットを作成する。
    - このデータセットを用いて既存のLLMを微調整する。
- この言語に特化したLLMアーキテクチャーを設計する。
- コードのフェーズ遷移:
    1. 編集（LSPリクエスト）時: レスポンスに必要な計算を行う。
    2. 実行前: 必要であれば実行時検査を行う。
    3. 実行時: コードを実行する。
- 一意（uniqueness） / 線形（linearity）型。
    - あるいはより一般に、分割（fractional） / 量的（quantitative）型。
- 順序（ordered）型。
    - 型状態（type state）への応用例あり。
- *n*ビット整数型（符号付き/符号無し）。
- 必要に応じて実行時検査される篩（refinement）型。
- バイト配列の篩型としてのUTF-8文字列型。
- 多相コードを存在化（existentialization）によってコンパイルする。
    - 極限まで最適化したい場合は、実行時コード生成によって多相コードを具象型に対して特殊化できる。（実行時単相化）
    - 型ケース（type case）も実現可能。
- スタックオーバーフローしない再帰関数。
- 言語のセマンティクスを理解したメモリアロケーターを使用する。
- リージョンベースのメモリ管理。
- 強参照サイクルが静的に防止される参照カウント。
- デフォルトでパックされた（アラインされていない）レイアウトを使用する。
- ポインタータギングを活用する。

## ランダムな資料

- [Using dependent types to express modular structure](https://doi.org/10.1145/512644.512670)
- [Type Universes as Kripke Worlds](https://doi.org/10.1145/3747532)
- [Linearity and Uniqueness: An Entente Cordiale](https://doi.org/10.1007/978-3-030-99336-8_13)
- [Functional Ownership through Fractional Uniqueness](https://doi.org/10.1145/3649848)
- [Law and Order for Typestate with Borrowing](https://doi.org/10.1145/3689763)
- [Rows and Capabilities as Modal Effects](https://arxiv.org/abs/2507.10301)
- [Multi-Stage Programming with Splice Variables](https://tsung-ju.org/icfp25/)
- [Compiling Swift Generics](https://download.swift.org/docs/assets/generics.pdf)
- [The Simple Essence of Monomorphization](https://doi.org/10.1145/3720472)
- [Existentialize Your Generics](https://doi.org/10.1145/3759426.3760975)
- [No Graphics API](https://www.sebastianaaltonen.com/blog/no-graphics-api)
