with Interfaces;

package Verified_Operand_Stack
  with SPARK_Mode
is
   use type Interfaces.Integer_32;

   Capacity : constant := 256;

   subtype Stack_Depth is Natural range 0 .. Capacity;
   type Operand_Stack is private;

   function Depth (Stack : Operand_Stack) return Stack_Depth;

   function Top_Value (Stack : Operand_Stack) return Interfaces.Integer_32
     with Pre => Depth (Stack) > 0;

   procedure Push
     (Stack : in out Operand_Stack;
      Value : Interfaces.Integer_32)
   with
     Pre  => Depth (Stack) < Capacity,
     Post => Depth (Stack) = Depth (Stack)'Old + 1
       and then Top_Value (Stack) = Value;

   function Pop (Stack : in out Operand_Stack) return Interfaces.Integer_32
   with
     Pre  => Depth (Stack) > 0,
     Post => Depth (Stack) = Depth (Stack)'Old - 1
       and then Pop'Result = Top_Value (Stack'Old);

private
   type Stack_Values is array (Positive range 1 .. Capacity)
     of Interfaces.Integer_32;

   type Operand_Stack is record
      Values : Stack_Values := [others => 0];
      Top    : Stack_Depth := 0;
   end record;
end Verified_Operand_Stack;
