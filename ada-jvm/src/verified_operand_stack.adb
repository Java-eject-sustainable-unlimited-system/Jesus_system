package body Verified_Operand_Stack
  with SPARK_Mode
is
   function Depth (Stack : Operand_Stack) return Stack_Depth is
     (Stack.Top);

   function Top_Value (Stack : Operand_Stack) return Interfaces.Integer_32 is
     (Stack.Values (Stack.Top));

   procedure Push
     (Stack : in out Operand_Stack;
      Value : Interfaces.Integer_32)
   is
   begin
      Stack.Top := Stack.Top + 1;
      Stack.Values (Stack.Top) := Value;
   end Push;

   function Pop (Stack : in out Operand_Stack) return Interfaces.Integer_32 is
      Value : constant Interfaces.Integer_32 := Stack.Values (Stack.Top);
   begin
      Stack.Top := Stack.Top - 1;
      return Value;
   end Pop;
end Verified_Operand_Stack;
