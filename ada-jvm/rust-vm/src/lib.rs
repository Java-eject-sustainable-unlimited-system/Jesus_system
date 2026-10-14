use std::fmt;

const STACK_CAPACITY: usize = 256;

#[derive(Debug, Eq, PartialEq)]
pub enum VmError {
    EmptyCode,
    StackOverflow,
    StackUnderflow,
    TruncatedOperand,
    DivisionByZero,
    MissingReturn,
    ReturnWithoutValue,
    UnsupportedOpcode(u8),
}

impl fmt::Display for VmError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::EmptyCode => write!(formatter, "empty bytecode"),
            Self::StackOverflow => write!(formatter, "operand stack overflow"),
            Self::StackUnderflow => write!(formatter, "operand stack underflow"),
            Self::TruncatedOperand => write!(formatter, "truncated bytecode operand"),
            Self::DivisionByZero => write!(formatter, "integer division by zero"),
            Self::MissingReturn => write!(formatter, "method bytecode ended without ireturn"),
            Self::ReturnWithoutValue => write!(formatter, "ireturn requires a stack value"),
            Self::UnsupportedOpcode(opcode) => write!(formatter, "unsupported opcode 0x{opcode:02x}"),
        }
    }
}

impl std::error::Error for VmError {}

/// Executes the integer bytecode subset supported by the Ada prototype.
pub fn execute(code: &[u8]) -> Result<i32, VmError> {
    if code.is_empty() {
        return Err(VmError::EmptyCode);
    }

    let mut stack = [0_i32; STACK_CAPACITY];
    let mut top = 0;
    let mut pc = 0;

    while pc < code.len() {
        let opcode = code[pc];
        pc += 1;

        match opcode {
            0x02..=0x08 => push(&mut stack, &mut top, i32::from(opcode) - 3)?,
            0x10 => {
                let operand = *code.get(pc).ok_or(VmError::TruncatedOperand)?;
                pc += 1;
                push(&mut stack, &mut top, i32::from(operand as i8))?;
            }
            0x60 => binary_op(&mut stack, &mut top, i32::wrapping_add)?,
            0x64 => binary_op(&mut stack, &mut top, i32::wrapping_sub)?,
            0x68 => binary_op(&mut stack, &mut top, i32::wrapping_mul)?,
            0x6c => {
                let right = pop(&stack, &mut top)?;
                let left = pop(&stack, &mut top)?;
                if right == 0 {
                    return Err(VmError::DivisionByZero);
                }
                let result = if left == i32::MIN && right == -1 {
                    i32::MIN
                } else {
                    left / right
                };
                push(&mut stack, &mut top, result)?;
            }
            0xac => return pop(&stack, &mut top).map_err(|_| VmError::ReturnWithoutValue),
            other => return Err(VmError::UnsupportedOpcode(other)),
        }
    }

    Err(VmError::MissingReturn)
}

fn push(stack: &mut [i32; STACK_CAPACITY], top: &mut usize, value: i32) -> Result<(), VmError> {
    if *top == STACK_CAPACITY {
        return Err(VmError::StackOverflow);
    }
    stack[*top] = value;
    *top += 1;
    Ok(())
}

fn pop(stack: &[i32; STACK_CAPACITY], top: &mut usize) -> Result<i32, VmError> {
    if *top == 0 {
        return Err(VmError::StackUnderflow);
    }
    *top -= 1;
    Ok(stack[*top])
}

fn binary_op(
    stack: &mut [i32; STACK_CAPACITY],
    top: &mut usize,
    operation: fn(i32, i32) -> i32,
) -> Result<(), VmError> {
    let right = pop(stack, top)?;
    let left = pop(stack, top)?;
    push(stack, top, operation(left, right))
}

#[cfg(test)]
mod tests {
    use super::{execute, VmError};

    #[test]
    fn executes_integer_arithmetic_and_signed_bipush() {
        assert_eq!(execute(&[0x10, 8, 0x10, 5, 0x60, 0xac]), Ok(13));
        assert_eq!(execute(&[0x02, 0x10, 3, 0x6c, 0xac]), Ok(0));
        assert_eq!(execute(&[0x10, 0xfd, 0xac]), Ok(-3));
        assert_eq!(execute(&[0x10, 7, 0x10, 6, 0x68, 0xac]), Ok(42));
        assert_eq!(execute(&[0x10, 9, 0x10, 4, 0x64, 0xac]), Ok(5));
    }

    #[test]
    fn preserves_java_integer_overflow_and_division_semantics() {
        assert_eq!(execute(&[0x05, 0x05, 0x68, 0xac]), Ok(4));
        assert_eq!(execute(&[0x02, 0x02, 0x6c, 0xac]), Ok(i32::MIN));
    }

    #[test]
    fn rejects_malformed_or_unsupported_bytecode() {
        assert_eq!(execute(&[]), Err(VmError::EmptyCode));
        assert_eq!(execute(&[0x10]), Err(VmError::TruncatedOperand));
        assert_eq!(execute(&[0x60, 0xac]), Err(VmError::StackUnderflow));
        assert_eq!(execute(&[0xac]), Err(VmError::ReturnWithoutValue));
        assert_eq!(execute(&[0x00]), Err(VmError::UnsupportedOpcode(0x00)));
        assert_eq!(execute(&[0x04]), Err(VmError::MissingReturn));
        assert_eq!(execute(&[0x02, 0x03, 0x6c, 0xac]), Err(VmError::DivisionByZero));
    }

    #[test]
    fn rejects_stack_overflow() {
        let mut code = vec![0x03; 257];
        code.push(0xac);
        assert_eq!(execute(&code), Err(VmError::StackOverflow));
    }
}
