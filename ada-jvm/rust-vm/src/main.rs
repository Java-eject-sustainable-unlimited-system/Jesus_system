use std::env;
use std::fs::File;
use std::io::Read;
use std::process::ExitCode;

const MAX_CODE_LENGTH: u64 = 65_536;

fn main() -> ExitCode {
    match run() {
        Ok(result) => {
            println!("{result}");
            ExitCode::SUCCESS
        }
        Err(error) => {
            eprintln!("Rust VM error: {error}");
            ExitCode::FAILURE
        }
    }
}

fn run() -> Result<i32, Box<dyn std::error::Error>> {
    let mut arguments = env::args_os();
    let program = arguments.next().unwrap_or_default();
    let Some(path) = arguments.next() else {
        return Err(format!(
            "usage: {} <raw-method-code-file>",
            program.to_string_lossy()
        )
        .into());
    };
    if arguments.next().is_some() {
        return Err(format!(
            "usage: {} <raw-method-code-file>",
            program.to_string_lossy()
        )
        .into());
    }

    let file = File::open(path)?;
    if file.metadata()?.len() > MAX_CODE_LENGTH {
        return Err("bytecode input exceeds 64 KiB".into());
    }
    let mut code = Vec::new();
    file.take(MAX_CODE_LENGTH + 1).read_to_end(&mut code)?;
    if code.len() as u64 > MAX_CODE_LENGTH {
        return Err("bytecode input exceeds 64 KiB".into());
    }
    Ok(jesus_rust_vm::execute(&code)?)
}
